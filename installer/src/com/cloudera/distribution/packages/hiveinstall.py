# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.Hiveinstall
#
# Defines the ToolInstall instance that installs Hive

import os
import sys

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.tools.dirutils as dirutils
import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output


class HiveInstall(ToolInstall):
  def __init__(self, properties):
    ToolInstall.__init__(self, "Hive", properties)
    self.addDependency("Hadoop")


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    pass

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    pass

  def getFinalInstallPath(self):
    return os.path.join(self.getInstallBasePath(), HIVE_INSTALL_SUBDIR)

  def install(self):
    """ Run the installation itself. """
    # install Hive by unzipping the package
    hivePackageName = os.path.abspath(os.path.join(PACKAGE_PATH, \
        HIVE_PACKAGE))
    if not os.path.exists(hivePackageName):
      raise InstallError("Missing hive installation package " \
          + hivePackageName)

    installPath = self.getInstallBasePath()
    dirutils.mkdirRecursive(installPath)

    if self.properties.getBoolean("output.verbose", False):
      verboseChar = "v"
    else:
      verboseChar = ""
    cmd = "tar -" + verboseChar + "zxf \"" + hivePackageName + "\"" \
        + " -C \"" + installPath + "\""

    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error unpacking hive (tar returned error)")

    # TODO: Set the HADOOP variable as necessary by modifying bin/hive


  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    self.createInstallSymlink("hive")
    configDirSrc = os.path.join(self.getFinalInstallPath(), "conf")
    self.createEtcSymlink("hive", configDirSrc)


  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify Hive


