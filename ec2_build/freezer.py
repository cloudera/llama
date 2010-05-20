#!/usr/bin/env python
# Copyright (c) 2009 Cloudera, inc.

import sys
import os
import boto
from boto.s3.key import Key
import tempfile
from optparse import OptionParser


S3 = boto.connect_s3()

BUILD_BUCKET_NAME = 'ec2-build'
BUILD_BUCKET = S3.lookup(BUILD_BUCKET_NAME)

FREEZER_BUCKET_NAME = 'cloudera-freezer'
FREEZER_BUCKET = S3.lookup(FREEZER_BUCKET_NAME)

BINARY_MAINDIR = "build/"
SOURCE_MAINDIR = "file-cache/"
OPTIONS = {}


if not BUILD_BUCKET:
  error_string = """Unable to lookup bucket %s
did you set your AWS_SECRET_ACCESS_KEY correctly""" % BUILD_BUCKET_NAME
  raise Exception(error_string)

if not FREEZER_BUCKET:
  error_string = """Unable to lookup bucket %s
did you set your AWS_SECRET_ACCESS_KEY correctly""" % FREEZER_BUCKET_NAME
  raise Exception(error_string)


def if_verbose_print(input_string):
  """ Only print if we are not quiet """
  if not OPTIONS.quiet:
    print(input_string)


def rename_key(src, dst):
  """ Moves all the files from src to destination """
  if OPTIONS.dry_run:
    if_verbose_print("Pretending to move")
  source_keys = [ key for key in FREEZER_BUCKET.list(prefix=src)]
  for source_key in source_keys:
    src_file = source_key.name
    dst_file = source_key.name
    dst_file = dst_file.replace(src,dst)
    source_key.name = source_key.name.replace('+','%2B')

    if_verbose_print("Copying from: %s to: %s" % (src_file, dst_file))
    if not OPTIONS.dry_run:
      source_key.copy(FREEZER_BUCKET_NAME, dst_file)

  source_keys = [ key for key in FREEZER_BUCKET.list(prefix=src)]
  for source_key in source_keys:
    src_file = source_key.name
    source_key.name = source_key.name.replace('+','%2B')
    if_verbose_print("Deleting: %s" % (src_file))
    if not OPTIONS.dry_run:
      source_key.delete()


def move_to_freezer(ec2_build_source, freezer_destination):
  """ Moves all the files from ec2_build_source to freezer_destination. Including files referenced
      in manifests"""

  if not ec2_build_source.endswith('/'):
    ec2_build_source = ec2_build_source + '/'

  if not freezer_destination.endswith('/'):
    freezer_destination = freezer_destination + '/'

  key_sw = BINARY_MAINDIR + ec2_build_source
  rs = BUILD_BUCKET.list(prefix=key_sw)

  binary_freezer_destination = freezer_destination + 'binary/'
  source_freezer_destination = freezer_destination + 'source/'

  for key in rs:

    binary_destination_path = key.name.replace(key_sw, binary_freezer_destination)
    if_verbose_print("Copying file [%s] to [%s]" % (key.name, binary_destination_path))
    if not OPTIONS.dry_run:
      key.copy(FREEZER_BUCKET, binary_destination_path)

    # Go through manifests and copy referenced files as well
    if key.name.endswith('manifest.txt'):
      for line in key.get_contents_as_string().split('\n'):
        type, src, hash, url = line.strip().split('\t')

        artifact_destination_patch = source_freezer_destination + src
        if_verbose_print("Copying artifact [%s] | hash: [%s] to [%s]" % (src, hash, artifact_destination_patch))

        if not OPTIONS.dry_run:
          source_artifact = BUILD_BUCKET.get_key(SOURCE_MAINDIR + hash)
          source_artifact.copy(FREEZER_BUCKET, artifact_destination_patch)




def main(args):
  usage = "usage: %prog [options] ec2_build_source freezer_destination"
  op = OptionParser(usage=usage)

  op.add_option('-d','--dry-run', action="store_true",default=False,
      help="Dont do anything just pretend")

  op.add_option('-r','--rename', action="store_true",default=False,
    help="All we do in this mode is rename a build from the source to the destination. The required arguments change to the source and destination of the move")

  op.add_option('-q','--quiet', action="store_true",default=False,
      help="Don't actually print anything")

  (options, args) = op.parse_args()
  global OPTIONS
  OPTIONS = options

  if len(args) != 2:
    op.error("I need more arguments")

  ec2_build_source = args[0]
  freezer_destination = args[1]

  if options.rename:
    rename_key(ec2_build_source, freezer_destination)
  else:
    move_to_freezer(ec2_build_source, freezer_destination)

if __name__ == "__main__":
  main(sys.argv)
