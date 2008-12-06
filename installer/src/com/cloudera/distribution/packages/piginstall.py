# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.piginstall
#
# Defines the ToolInstall instance that installs Pig

import os
import sys

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.tools.dirutils as dirutils
import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output


class PigInstall(ToolInstall):
  def __init__(self, properties):
    ToolInstall.__init__(self, "Pig", properties)
    self.addDependency("Hadoop")


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    pass
    # TODO: Does pig require perl, still?

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    pass
    # TODO: Config pig Hadoop version (pig-types defaults to '17') in bin/pig
    # pig-env.sh ?
    # TODO: conf/pig.properties must be rewritten by this tool.


  def getFinalInstallPath(self):
    return os.path.join(self.getInstallBasePath(), PIG_INSTALL_SUBDIR)

  def install(self):
    """ Run the installation itself. """
    # install Pig by unzipping the package
    pigPackageName = os.path.abspath(os.path.join(PACKAGE_PATH, \
        PIG_PACKAGE))
    if not os.path.exists(pigPackageName):
      raise InstallError("Missing pig installation package " \
          + pigPackageName)

    installPath = self.getInstallBasePath()
    dirutils.mkdirRecursive(installPath)

    if self.properties.getBoolean("output.verbose", False):
      verboseChar = "v"
    else:
      verboseChar = ""
    cmd = "tar -" + verboseChar + "zxf \"" + pigPackageName + "\"" \
        + " -C \"" + installPath + "\""

    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error unpacking pig (tar returned error)")

    # TODO: Write out any config files here.

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    self.createInstallSymlink("pig")
    configDirSrc = os.path.join(self.getFinalInstallPath(), "conf")
    self.createEtcSymlink("pig", configDirSrc)


  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify Pig


