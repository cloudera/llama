#!/usr/bin/env python

from optparse import OptionParser
import os
import os.path
import re
import sys
import md5
import tempfile
import subprocess
import shutil
import glob
import fnmatch

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



def simple_exec(command, cwd=None, env=None):
  '''
  Execute a command and returns its output

  @param command Command to execute. This is a list including the actual command and its parameters
  @return Result of stdout as a string
  '''

  command = ['/bin/bash', '-c'] + [' '.join(command)]

  return subprocess.Popen(command, cwd=cwd, stdout=subprocess.PIPE, shell=False, env=env).communicate()[0]


def rpm_md5(rpm):
    tempdir = tempfile.mkdtemp()

    simple_exec(['cp', rpm, tempdir], cwd=tempdir)
    simple_exec(['rpm2cpio', os.path.basename(rpm), '|', 'cpio', '-idm'], cwd=tempdir)
    temptar = glob.glob(os.path.join(tempdir, "*.tar.gz"))[0]

    md5_str = md5file(temptar)

    simple_exec(['rm', '-rf', tempdir])

    return md5_str
    


def deb_md5(deb):
    return md5file(deb)

def tar_md5(tar):
    return md5file(tar)



def parse_args():
  """ Parse command line arguments into globals. """

  op = OptionParser()

  op.add_option('-r', '--rpm',
                default=None,
                help="Location of source rpm to check.")
  op.add_option('-d', '--deb',
                default=None,
                help="Location of source deb to check.")
  op.add_option('-t', '--tar',
                default=None,
                help="Location of source tarball to check.")
  op.add_option('--dir',
                default=None,
                help="Location of root dir to check.")
  
  

  opts, args = op.parse_args()

  if len(args):
      op.print_usage()
      raise Exception("Unhandled args: %s" % repr(args))

#  if not opts.rpm:
#      op.error('--rpm is mandatory.')

#  if not opts.deb:
#      op.error('--deb is mandatory.')

#  if not opts.tar:
#      op.error('--tar is mandatory.')

#  if not os.path.exists(opts.rpm):
#      op.error("Source RPM %s does not exist." % (opts.rpm, ))

  if not opts.dir:
      op.error('--dir is mandatory.')

  if not os.path.exists(opts.dir):
      op.error("Root dir %s does not exist." % (opts.dir, ))


#  if not os.path.exists(opts.deb):
#      op.error("Source deb %s does not exist." % (opts.deb, ))

#  if not os.path.exists(opts.tar):
#      op.error("Source tar %s does not exist." % (opts.tar, ))

  return opts

def find_pkgs(rootdir):
    pkgs = dict()
    
    for d in os.listdir(rootdir):
        projdir = os.path.join(rootdir, d)
        if os.path.isdir(projdir):
            tar = ''
            deb = ''
            rpm = ''
            for f in os.listdir(projdir):
                if fnmatch.fnmatch(f, "*.src.rpm"):
                    rpm = os.path.join(projdir, f)
                elif fnmatch.fnmatch(f, "*.orig.tar.gz"):
                    deb = os.path.join(projdir, f)
                elif fnmatch.fnmatch(f, "*.tar.gz"):
                    tar = os.path.join(projdir, f)

            pkgs[d] = [tar, deb, rpm]

    return pkgs
        

def main():
    options = parse_args()

    pkgs = find_pkgs(options.dir)

    md5_results = dict()

    found_bad = 0
    
    for pkg in iter(pkgs):
        print "Checking tarball integrity for project %s" % (pkg, )
        md5_tar = tar_md5(pkgs[pkg][0])
        md5_deb = deb_md5(pkgs[pkg][1])
        md5_rpm = rpm_md5(pkgs[pkg][2])

        if md5_tar == md5_rpm and md5_tar == md5_deb:
            md5_results[pkg] = "All good"
        else:
            md5_results[pkg] = "Tar, source rpm and source deb do not all match."
            found_bad = 1

    for pkg in iter(md5_results):
        print "Tarball and source package check for %s: %s" % (pkg, md5_results[pkg])

    if found_bad:
        sys.exit("At least one project failed to validate tarballs and source packages.")
    else:
        sys.exit(0)
        
if __name__ == "__main__":
  main()
