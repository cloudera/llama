#!/usr/bin/python

import sys, time
import platform
import cloudera.utils
from cloudera.packaging.packaging import PackageManagerFactory


PACKAGE_FUSE_NAME = "hadoop-0.20-fuse"


def main():
  packageManagerFactory = PackageManagerFactory()

  packageManager = packageManagerFactory.create_package_manager()


  ####################
  # Look for package #
  ####################

  fuse_pkg = packageManager.search_from_name(PACKAGE_FUSE_NAME)
  print str(fuse_pkg)
  if len(fuse_pkg) == 0:
     raise Exception(PACKAGE_FUSE_NAME + " not available")


  ###################
  # Install package #
  ###################


  (arch, format) = platform.architecture()
  current_arch = 'i386'
  if arch == '64bit':
    current_arch = 'current'

  for pkg in fuse_pkg:
    pkg.set_preferred_arch(current_arch)
  packageManager.install(fuse_pkg)

  for fuse in fuse_pkg:
    if not packageManager.is_package_installed(fuse.name()):
      raise Exception(fuse.name() + " could not be installed")



  #########################
  # Check for fuse module #
  #########################

  (rc, output) = cloudera.utils.simple_exec2(["lsmod"])
  lines = [line for line in output if line.startswith('fuse ')]
  num_fuse_module = len(lines)
  if num_fuse_module < 1:
    print "No fuse module in kernel"
    print "Loading fuse kernel module"

    rc = cloudera.utils.simple_exec(["modprobe", 'fuse'])
    if rc:
      raise Exception("Couldn't load fuse kernel module")

  #########################
  # Mount fuse filesystem #
  #########################

  fuse_test_dir = '/tmp/fuse_test_fs'

  # Step 1: create a dir
  rc = cloudera.utils.simple_exec(['mkdir', fuse_test_dir])
  if rc:
     raise Exception("Couldn't create directory " + fuse_test_dir)

  # Step 2: mount it!  # We shouldn't do that, but we suppose namenode/datanode are up because the previous script started them
  (rc, output) = cloudera.utils.simple_exec2(['hadoop-fuse-dfs', 'dfs://localhost:8020',fuse_test_dir])

  for line in output:
    print line

  if rc:
     raise Exception("Couldn't mount hdfs fuse directory to " + fuse_test_dir + "\nReturn code: " + str(rc))


  # Step 3: Check it exists in mtab

  try_num = 0
  while len([mount for mount in open("/etc/mtab", 'r') if mount.startswith('fuse ') and  fuse_test_dir in mount]) != 1 and try_num < 5:
    time.sleep(5)
    try_num = try_num + 1


  mounts = [mount for mount in open("/etc/mtab", 'r') if mount.startswith('fuse ' + fuse_test_dir)]
  num_fuse_mtab = len(mounts)

  if num_fuse_mtab < 1:
     raise Exception("No fuse mount in mtab")

  if num_fuse_mtab > 1:
     raise Exception("Several references to fuse mount in mtab")


  # Step 4: Unmount it
  rc = cloudera.utils.simple_exec(['fusermount', '-u', fuse_test_dir])
  if rc:
     raise Exception("Couldn't unmount hdfs fuse directory from " + fuse_test_dir)

  #####################
  # Uninstall package #
  #####################

  packageManager.uninstall(fuse_pkg)

  for fuse in fuse_pkg:
    if packageManager.is_package_installed(fuse.name()):
      raise Exception(PACKAGE_FUSE_NAME + " could not be uninstalled")

  print "Exiting script."
  sys.exit(0)

main()
