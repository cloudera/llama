#!/usr/bin/env python2.5
# (c) Copyright 2009 Cloudera, Inc.
__usage = """
   --bucket | -b <bucket> bucket to dump sources and build products into
                          (default: ec2-build)

   --key | -k  <key>      SSH key to allow connection to build slave instances
                          (default: current user name)

   --groups | -g <group>  EC2 Access Groups, comma-separated, to use on build
                          slaves
                          (default: cloudera, <username>)

   --only <rpm|deb>       Only rebuild RPMs/debs
   --only <distro>        Only build on given distro (eg centos5)
   --only <arch>          Only build slaves of given arch (eg amd64)
   --only <distro-arch>   Only build slaves of given distro-arch

                          The --only options may be listed multiple times,
                          in which case the union of the sets is built.

   --dry-run | -n         Don't actually take any actions - just print out what
                          would normally happen

   --package | -p <pkg>   Select package to build
"""

import boto
import datetime
import md5
from optparse import OptionParser
import os
import re
import sys
import time

class Options:
  def __init__(self):
    # Bucket to store source RPMs/debs in
    self.S3_BUCKET = 'ec2-build'
    self.EC2_KEY_NAME = os.getlogin()
    self.EC2_GROUPS=['cloudera', os.getlogin()]
    self.BUILD_MACHINES = DEFAULT_BUILD_MACHINES
    self.DRY_RUN = False
    # Default to building hadoop
    self.PACKAGE = 'hadoop'
  pass

SCRIPT_DIR = os.path.realpath(os.path.dirname(sys.argv[0]))

# User running the script
USERNAME = os.getlogin()

# Build ID
BUILD_ID = "%s-%s" % (USERNAME, datetime.datetime.now().strftime("%Y%m%d_%H%M%S"))

# TODO(todd) this is kind of gross - is there no nicer way of doing this?
REDIST_DIR = os.path.join(SCRIPT_DIR, "../../../bin/redist")

# Where to find source packages TODO(todd) should be pulled from a VerStringTarget maybe?
HD_DEB_ROOT = REDIST_DIR + "/projects/HadoopDist/deb_hadoop_deb/hadoop-deb-0.3.0-test"
HD_RPM_ROOT = REDIST_DIR + "/projects/HadoopDist/rpm_hadoop_srpm/hadoop-srpm-0.3.0-test/topdir/SRPMS"
PIG_DEB_ROOT = REDIST_DIR + "/projects/HadoopDist/deb_pig_deb/pig-deb"
PIG_RPM_ROOT = REDIST_DIR + "/projects/HadoopDist/rpm_pig_srpm/pig-srpm/topdir/SRPMS"
HIVE_DEB_ROOT = REDIST_DIR + "/projects/HadoopDist/deb_hive_deb/hive-deb"
HIVE_RPM_ROOT = REDIST_DIR + "/projects/HadoopDist/rpm_hive_srpm/hive-srpm/topdir/SRPMS"

# Files to upload
# TODO(todd) this is kind of awful - maybe we should parse .changes files
PACKAGE_FILES = {
  'hadoop': {
    'deb': [os.path.join(HD_DEB_ROOT, x) for x in
            ["hadoop_0.18.3-2cloudera0.3.0_source.changes",
             "hadoop_0.18.3-2cloudera0.3.0.diff.gz",
             "hadoop_0.18.3-2cloudera0.3.0.dsc",
             "hadoop_0.18.3.orig.tar.gz"]],
    'rpm': [os.path.join(HD_RPM_ROOT, x) for x in
          ["hadoop-0.18.3-9.cloudera.CH0_3.src.rpm"]],
    },
  'pig': {
    'deb': [os.path.join(PIG_DEB_ROOT, x) for x in
            ["pig_0.1.1-0cloudera0.3.0.dsc",
             "pig_0.1.1-0cloudera0.3.0.diff.gz",
             "pig_0.1.1-0cloudera0.3.0_source.changes",
             "pig_0.1.1.orig.tar.gz"]],
    'rpm': [os.path.join(PIG_RPM_ROOT, x) for x in
            ["hadoop-pig-0.1.1-9.cloudera.CH0_3.src.rpm"]],
    },
  'hive': {
    'deb': [os.path.join(HIVE_DEB_ROOT, x) for x in
            ["hive_0.3~svn759018-0cloudera0.3.0.dsc",
             "hive_0.3~svn759018-0cloudera0.3.0_source.changes",
             "hive_0.3~svn759018-0cloudera0.3.0.diff.gz",
             "hive_0.3~svn759018.orig.tar.gz"]],
    'rpm': [os.path.join(HIVE_RPM_ROOT, x) for x in
            ["hadoop-hive-r759018_branch0.3-9.cloudera.CH0_3.src.rpm"]],
    },
  }

# Directory in S3_BUCKET to put files
FILECACHE_S3="file-cache"

# How long should manifests be valid for
EXPIRATION=60*60*6 # 6h

# dict from (distro, arch) => AMI ID
# These AMIs must run their userdata on startup if it has
# a shebang!
AMIS={
# Alestic ubuntu/debian AMIs
  ('intrepid', 'x86'):   'ami-ec48af85',
  ('intrepid', 'amd64'): 'ami-e057b089',
  ('hardy', 'x86'):      'ami-ef48af86',
  ('hardy', 'amd64'):    'ami-e257b08b',
  ('lenny', 'x86'):      'ami-e348af8a',
  ('lenny', 'amd64'):    'ami-fb57b092',
  ('etch', 'x86'):       'ami-e248af8b',
  ('etch', 'amd64'):     'ami-fd57b094',
# home built centos5 AMIs
  ('centos5', 'x86'):    'ami-c950b7a0',
  ('centos5', 'amd64'):  'ami-a750b7ce',
  }

# What kind of instances should be started to run the various builds
BUILD_INSTANCE_TYPES = {
  'x86':   'm1.small',
  'amd64': 'm1.large',
  }

# Shell scripts to run to perform builds
BUILD_DEB=file(SCRIPT_DIR + "/build_deb.sh").read()
BUILD_RPM=file(SCRIPT_DIR + "/build_rpm.sh").read()

# These values are actually the entire shell script contents, which
# we need to pass through boto
BUILD_SCRIPTS = {
  'deb': BUILD_DEB,
  'rpm': BUILD_RPM,
  }

# What we actually want to build
# tuples of (build type, distro, arch)
DEFAULT_BUILD_MACHINES = [
  ('deb', 'intrepid', 'x86'),
  ('deb', 'intrepid', 'amd64'),
  ('deb', 'hardy',    'x86'),
  ('deb', 'hardy',    'amd64'),
  ('deb', 'lenny',    'x86'),
  ('deb', 'lenny',    'amd64'),
  ('deb', 'etch',     'x86'),
  ('deb', 'etch',     'amd64'),
  ('rpm', 'centos5',  'x86'),
  ('rpm', 'centos5',  'amd64'),
  ]

def parse_args():
  """ Parse command line arguments into globals. """

  ret_opts = Options()

  op = OptionParser(usage = __usage)
  op.add_option('-b', '--bucket')
  op.add_option('-k', '--key')
  op.add_option('-g', '--groups')
  op.add_option('--only', action='append')
  op.add_option('-n', '--dry-run', action='store_true')
  op.add_option('-p', '--package', action='store', choices=PACKAGE_FILES.keys())

  opts, args = op.parse_args()
  if len(args):
    op.print_usage()
    raise Exception("Unhandled args: %s" % repr(args))

  if opts.groups:
    ret_opts.EC2_GROUPS = groups.split(',')

  if opts.only:
    ret_opts.BUILD_MACHINES = (
      [(type, distro, arch) for
       (type, distro, arch) in ret_opts.BUILD_MACHINES
       if (type in opts.only or
           distro in opts.only or
           arch in opts.only or
           "%s-%s"%(distro, arch) in opts.only)])

  ret_opts.DRY_RUN = opts.dry_run
  if opts.bucket:
    ret_opts.S3_BUCKET = opts.bucket

  if opts.key:
    ret_opts.EC2_KEY_NAME = opts.key

  if opts.package:
    ret_opts.PACKAGE = opts.package

  return ret_opts

def md5file(filename):
  """ Return the hex digest of a file without loading it all into memory. """
  fh = file(filename)
  digest = md5.new()
  while 1:
    buf = fh.read(4096)
    if buf == "":
      break
    digest.update(buf)
  fh.close()
  return digest.hexdigest()

def progressbar(bytes_done, total_bytes):
  """ Display a progress bar for boto file upload callback """
  print "Sent % 5d/%d KB" % (bytes_done/1024, total_bytes/1024),

  width = 60
  bar_len = (width * bytes_done / total_bytes)
  print "[" + ("=" * bar_len) + ">" + (" " * (width - bar_len - 1)) + "]",
  print "\r",
  sys.stdout.flush()

def satisfy_in_cache(bucket, path):
  """
  Ensure that the file at path 'path' is present in the file cache
  in bucket 'bucket'. Presence is determined by the md5sum of the
  file. If it is not present, uploads it.

  @param bucket an S3Bucket instance
  @param path a path on the local filesystem
  """
  checksum = md5file(path)

  print >>sys.stderr, "Trying to satisfy %s from cache..." % os.path.basename(path)
  s3_cache_path = "%s/%s" % (FILECACHE_S3, checksum)
  if not bucket.lookup(s3_cache_path):
    print >>sys.stderr, "not yet in cache - uploading..."
    k = bucket.new_key(s3_cache_path)
    k.set_contents_from_filename(path, cb=progressbar, num_cb=1000)
    print >>sys.stderr, "done"

  k = bucket.lookup(s3_cache_path)

  return (checksum, k.generate_url(EXPIRATION))

def upload_files_and_manifest(options):
  """
  Upload all of the required files as well as a manifest.txt file into
  the BUILD_ID dir on S3.
  """
  s3 = boto.connect_s3()
  bucket = s3.lookup(options.S3_BUCKET)

  build_dir = os.path.join("build", BUILD_ID)

  manifest_list = []

  files = PACKAGE_FILES[options.PACKAGE]
  for tag, paths in files.iteritems():
    for path in paths:
      dest = os.path.join(build_dir, os.path.basename(path))
      (checksum, url) = satisfy_in_cache(bucket, path)
      manifest_list.append((tag, os.path.basename(path), checksum, url))

  manifest = "\n".join(
    ["\t".join(el) for el in manifest_list])

  if not options.DRY_RUN:
    man_key = bucket.new_key('%s/manifest.txt' % build_dir)
    man_key.set_contents_from_string(
      manifest,
      headers={'Content-Type': 'text/plain'})
    return man_key.generate_url(EXPIRATION)
  else:
    return "<manifest not uploaded - dry run>"

def main():
  options = parse_args()
  manifest_url = upload_files_and_manifest(options)
  print manifest_url

  ec2 = boto.connect_ec2()

  instances = []
  for build_type, distro, arch in options.BUILD_MACHINES:
    ami = AMIS[(distro, arch)]
    image = ec2.get_image(ami)
    instance_type = BUILD_INSTANCE_TYPES[arch]
    start_script = BUILD_SCRIPTS[build_type]

    subs = {
      'build_id': BUILD_ID,
      'username': USERNAME,
      'distro': distro,
      'manifest_url': manifest_url,
      's3_bucket': options.S3_BUCKET,
      'aws_access_key_id': ec2.aws_access_key_id,
      'aws_secret_access_key': ec2.aws_secret_access_key,
      }

    subbed_script = start_script.replace(
      "##SUBSTITUTE_VARS##",
      """
      BUILD_ID='%(build_id)s'
      BUILD_USER='%(username)s'
      DISTRO='%(distro)s'
      MANIFEST_URL='%(manifest_url)s'
      S3_BUCKET='%(s3_bucket)s'
      AWS_ACCESS_KEY_ID='%(aws_access_key_id)s'
      AWS_SECRET_ACCESS_KEY='%(aws_secret_access_key)s'
      """ % subs)

    print "Starting %s-%s build slave (%s)..." % (distro, arch, ami)
    if not options.DRY_RUN:
      reservation = image.run(
        key_name=options.EC2_KEY_NAME,
        security_groups=options.EC2_GROUPS,
        user_data=subbed_script,
        instance_type=instance_type)
      instances.append(reservation.instances[0])
    else:
      print "   [dry run: not starting]"

  print "Waiting for instances to boot..."
  for instance in instances:
    instance.update()
    while instance.state != 'running':
      print "   waiting on %s to start..." % instance.id
      time.sleep(5)
      instance.update()
    print "   Booted %s at %s" % (instance.id, instance.dns_name)

  print "All build slaves booted!"
  print
  print "To killall: "
  print "  ec2-terminate-instances %s" % (" ".join([i.id for i in instances]))
  print
  print "Expect results at s3://%s/build/%s/" % (options.S3_BUCKET, BUILD_ID)
  print "To update apt repo after build is finished:"
  print "  update_repo.sh %s %s" % (options.S3_BUCKET, BUILD_ID)


if __name__ == "__main__":
  main()
