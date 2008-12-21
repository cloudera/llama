# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.arch
#
# Determine architecture-specific values and act as a factory
# for architecture-specific installers

import os
import platform
import sys

import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output

ARCH_UNKNOWN = 0
ARCH_I386    = 1
ARCH_X86_64  = 2

PLATFORM_UNKNOWN = 0
PLATFORM_UBUNTU  = 1
PLATFORM_FEDORA  = 2
PLATFORM_RHEL    = 3
PLATFORM_CENTOS  = 4

PACKAGE_MGR_UNKNOWN = 0
PACKAGE_MGR_DEBIAN  = 1
PACKAGE_MGR_RPM     = 2

# singleton instance of ArchDetector class
archDetector = None

def getArchDetector():
  """ ArchDetector is used as a singleton class. This is the factory """
  global archDetector
  if archDetector == None:
    archDetector = ArchDetector()

  return archDetector



class ArchDetector(object):
  """ Detect the architecture that we have available

      External dependencies:
        python 3.1 uses platform.linux_distribution() if available
        Also tries to use lsb_release and awk programs
        Falls back to platform.dist()
  """

  def __init__(self):
    self.arch = ARCH_UNKNOWN
    self.platform = PLATFORM_UNKNOWN
    self.packageMgr = PACKAGE_MGR_UNKNOWN

  def getArch(self):
    return self.arch

  def getPlatform(self):
    return self.platform

  def getPackageMgr(self):
    return self.packageMgr

  def getPackageMgrBin(self):
    """
    Get the package manager binary
    Returns None if the package
    manager is unknown
    """
    pckg_mgr = self.getPackageMgr()
    if pckg_mgr == PACKAGE_MGR_DEBIAN:
      return "apt-get"
    elif pckg_mgr == PACKAGE_MGR_RPM:
      return "yum"
    else:
      return None

  def scan(self):
    """ scans for platform and architecture """

    self.scanForDistribution()
    self.scanForArch()

  def scanForArch(self):

    machineType = platform.machine()
    if machineType == 'i386' or machineType == 'i586' or machineType == 'i686':
      output.printlnVerbose("Detected i386 architecture")
      self.arch = ARCH_I386
    elif machineType == 'x86_64':
      output.printlnVerbose("Detected x86_64 architecture")
      self.arch = ARCH_X86_64
    else:
      output.printlnInfo("Unrecognized machine architecture: " + machineType)


  def scanForDistribution(self):
    distributionName = None

    if hasattr(platform, "linux_distribution"):
      # use this, as it's the most up-to-date correct way to determine
      # this value. Only valid in Python 3.1 though
      output.printlnDebug("Trying platform.linux_distribution()")
      (distributionName, ignored, ignored2) =  platform.linux_distribution()
      output.printlnVerbose("Got distribution name: " + distributionName)

    if distributionName == None:
      # couldn't find it from platform.linux_distribution()
      # invoke lsb_release program if available
      output.printlnDebug("Trying lsb_release program")
      try:
        distributionLines = shell.shLines("lsb_release -d | awk '{ print $2 }'")
        if len(distributionLines) > 0:
          distributionName = distributionLines[0].strip()
        output.printlnVerbose("Got distribution name: " + distributionName)
      except CommandError, ce:
        # couldn't run this command
        output.printlnDebug("Could not run lsb_release:" + str(ce))


    if distributionName == None:
      # check using older platform.dist()
      # this is the least reliable method
      output.printlnDebug("Trying platform.dist()")
      (distributionName, ignored, ignored2) = platform.dist()
      output.printlnVerbose("Got distributionName: " + distributionName)


    if distributionName == "Ubuntu":
      output.printlnVerbose("Found linux distribution: Ubuntu")
      self.platform = PLATFORM_UBUNTU
      self.packageMgr = PACKAGE_MGR_DEBIAN
    elif distributionName == 'debian':
      output.printlnInfo(
        "Found linux distribution 'debian'; marking as Ubuntu")
      self.platform = PLATFORM_UBUNTU
      self.packageMgr = PACKAGE_MGR_DEBIAN
    elif distributionName == 'Fedora' or distributionName == 'fedora':
      output.printlnVerbose("Found linux distribution: Fedora")
      self.platform = PLATFORM_FEDORA
      self.packageMgr = PACKAGE_MGR_RPM
    else:
      output.printlnInfo("Could not identify linux distribution")
      self.platform = PLATFORM_UNKNOWN
      self.packageMgr = PACKAGE_MGR_UNKNOWN

    # TODO(aaron): 0.2 What are the signatures for RHEL, CentOS from this?
    # need to test py 3.1, py 2.5, lsb_release on those platforms.




