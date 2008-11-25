# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.hadoopinstall
#
# Defines the ToolInstall instance that installs Hadoop

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.util.output as output


class HadoopInstall(ToolInstall):
  def __init__(self):
    ToolInstall.__init__(self, "Hadoop")


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    # TODO precheck hadoop
    pass

  def install(self):
    """ Run the installation itself. """
    pass
    # TODO install hadoop

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    # TODO config hadoop
    pass

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    # TODO postinstall hadoop
    pass

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify hadoop


