# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.globalprereq
#
# Defines the ToolInstall instance that performs the global
# prerequisite configuration
#   e.g., get the user's ssh key file for slave machines, etc.

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.util.output as output


class GlobalPrereqInstall(ToolInstall):
  def __init__(self, properties):
    ToolInstall.__init__(self, "GlobalPrereq", properties)


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    # TODO: Any globally-required prerequisites are checked here.
    pass

  def install(self):
    """ Run the installation itself. """
    # This does nothing globaly
    pass

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    # TODO: Any global config settings are retrieved here
    # ssh key for installation user on all slave machines
    # installation username on all slave machines
    # upload prefix on slave machines (e.g., /tmp/)
    # install prefix (/usr/share/cloudera)
    pass

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    # This does nothing globally
    pass

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # This does nothing globally


