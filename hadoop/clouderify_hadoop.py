#!/usr/bin/env python
#
# (c) Copyright 2009 Cloudera, Inc.

from getopt import getopt
import os
import re
import shutil
import subprocess
import sys

import dist_options

HADOOPDIST_DIR = os.path.dirname(sys.argv[0])
DST_DIR = None
DRY_RUN = False
VERBOSE = True
HADOOP_VERSION = None
RELEASE_VERSION = None

REVERSE = False

def check_call(*popenargs, **kwargs):
  """
  Backported subprocess.check_call from python2.5 - we need
  to build on some systems that don't have python2.5 available.
  """
  retcode = subprocess.call(*popenargs, **kwargs)
  cmd = kwargs.get("args")
  if cmd is None:
    cmd = popenargs[0]
  if retcode:
    raise Exception("%s returned code %d" % (cmd, retcode))
  return retcode


def parse_args(argv):
  """Parse command line arguments and set globals."""
  global DST_DIR, HADOOP_VERSION, RELEASE_VERSION, REVERSE

  opts, args = getopt(argv[1:], "Rnv")

  if len(args) != 3:
    raise "usage: %s [-R] [-n] [-v] <hadoop dir to clouderify> <hadoop-version> <cloudera-version>" % argv[0]

  DST_DIR = args[0]
  if not os.path.exists(DST_DIR):
    raise "destination hadoop directory at %s does not exist" % DST_DIR

  HADOOP_VERSION = args[1]
  RELEASE_VERSION = args[2]

  for (opt, val) in opts:
    if opt == '-R':
      REVERSE = True
    elif opt == '-n':
      DRY_RUN = True
    elif opt == '-v':
      VERBOSE = True

def patch_file(path):
  """Return absolute path to a given patch file."""
  return os.path.join(HADOOPDIST_DIR, dist_options.PATCH_DIR, path)

def source_path(path):
  """Return absolute path to a file in the destination dir."""
  return os.path.join(HADOOPDIST_DIR, dist_options.SOURCE_DIR, path)

def dst_path(path):
  """Return absolute path to a file in the source dir."""
  return os.path.join(DST_DIR, path)

def docmd(argv):
  """Execute the given command unless dry run is enabled.

  Throws an exception if the exit code is nonzero.
  """
  if DRY_RUN or VERBOSE:
    print >>sys.stderr, "[exec] ", " ".join(argv)
  if not DRY_RUN:
    check_call(argv)

def ensure_dir(dir):
  """Ensure that a dir exists (equivalent to mkdir -p)."""
  parts = dir.split(os.sep)

  for i in xrange(1, len(parts)):
    prefix_path = os.sep.join(parts[:i+1])
    if not os.path.exists(prefix_path):
      if DRY_RUN or VERBOSE:
        print >>sys.stderr, "[mkdir] %s" % prefix_path
      if not DRY_RUN:
        os.mkdir(prefix_path)

def apply_patch(patch_file):
  """Apply the patch at the given path to DST_DIR, unless
  dry run is enabled.

  Throws an exception on patch failure.
  """
  if DRY_RUN or VERBOSE:
    print >>sys.stderr, "[patch] ", patch_file
  if not DRY_RUN:
    check_call(['patch', '-N', '-p0', '-d', DST_DIR],
                          stdin=file(patch_file))
  else:
    check_call(['patch', '--dry-run', '-p0', '-d', DST_DIR],
                          stdin=file(patch_file))

def unapply_patch(patch_file):
  """Unapply the patch at the given path to DST_DIR, unless
  dry run is enabled.

  Throws an exception on patch failure.
  """
  if DRY_RUN or VERBOSE:
    print >>sys.stderr, "[unpatch] ", patch_file
  if not DRY_RUN:
    check_call(['patch', '-R', '-p0', '-d', DST_DIR],
                          stdin=file(patch_file))
  else:
    check_call(['patch', '--dry-run', '-R', '-p0', '-d', DST_DIR],
                          stdin=file(patch_file))

def do_copy(src, dst):
  """Copy a file unless in dry run."""
  if DRY_RUN or VERBOSE:
    print >>sys.stderr, "[copy] %s => %s" % (src, dst)
  if not DRY_RUN:
    shutil.copy2(src, dst)

def do_move(src, dst):
  """Move file from src to dst, overwriting existing file if it exists."""
  if VERBOSE or DRY_RUN:
    print >>sys.stderr, "[move] %s => %s" % (src, dst)
  if not DRY_RUN:
    shutil.move(src, dst)

def do_rm(path):
  """Remove given file unless in dry run mode."""
  if VERBOSE or DRY_RUN:
    print >>sys.stderr, "[rm] %s" % path
  if not DRY_RUN:
    os.remove(path)

def apply_patches():
  """Apply all of the patches listed in the PATCHES configuration."""
  for patch_filename in dist_options.PATCHES:
    apply_patch(patch_file(patch_filename))

def unapply_patches():
  """Unapply all of the patches listed in PATCHES."""
  rev_patches = list(dist_options.PATCHES)
  rev_patches.reverse() # reverse is in-place!
  for patch_filename in rev_patches:
    unapply_patch(patch_file(patch_filename))

def copy_files():
  """Copy files as specified in dist_options.COPY_FILES."""
  for srcs, dst in dist_options.COPY_FILES:
    # verify dst dir exists
    ensure_dir(dst_path(os.path.dirname(dst)))

    if type(srcs) == str:
      srcs = [srcs]
    for src in srcs:
      if os.path.isfile(dst_path(dst)):
        do_copy(dst_path(dst), dst_path(dst + ".orig"))
      do_copy(source_path(src), dst_path(dst))

def uncopy_files():
  """Remove files copied from the cloudera directory."""
  for srcs, dst in dist_options.COPY_FILES:
    if type(srcs) == str:
      srcs = [srcs]
    for src in srcs:
      if os.path.exists(dst_path(dst + ".orig")):
        do_move(dst_path(dst + ".orig"), dst_path(dst))
      else:
        do_rm(dst_path(dst))

def hook_build_xml():
  """Modify the hadoop build.xml to rename the package target to package.orig.

  This allows us to hook in our modifications to the build product before the tarball
  dist step.
  """
  do_copy(dst_path("build.xml"), dst_path("build.xml.orig"))
  if VERBOSE or DRY_RUN:
    print >>sys.stderr, "[modifying package target to package.orig in build.xml]"
  if not DRY_RUN:
    orig_build_xml = file(dst_path("build.xml")).read()
    (new_xml, count) = \
              re.subn(r'(<target[^>]+name=)"package"',
                      lambda match: '%s"package.orig"' % match.group(1),
                      orig_build_xml)
    assert count == 1
    out = file(dst_path("build.xml"), "w")
    out.write(new_xml)
    out.close()

def unhook_build_xml():
  """Restore the original build.xml file."""
  do_move(dst_path("build.xml.orig"), dst_path("build.xml"))

def main(argv):
  parse_args(argv)

  if not REVERSE:
    copy_files()
    apply_patches()
    hook_build_xml()
  else:
    unhook_build_xml()
    unapply_patches()
    uncopy_files()

if __name__ == "__main__":
  main(sys.argv)
