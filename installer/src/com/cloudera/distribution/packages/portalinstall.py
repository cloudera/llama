# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.portalinstall
#
# Defines the ToolInstall instance that installs the web portal

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall

import com.cloudera.util.output as output


class PortalInstall(ToolInstall):
  def __init__(self, properties):
    ToolInstall.__init__(self, "Portal", properties)

    # need LogMover because we need MySQL
    self.addDependency("LogMover")


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    pass

  def install(self):
    """ Run the installation itself. """

    # the portal is only installed on the NN
    if self.isMaster():
      self.install_httpd()

    # TODO:
    # - update hadoop-site-location with the right location of hadoop-site.xml
    # - put the portal code in default htdocs for Lighttpd

  def install_httpd(self):
    """Installs Lighttpd with PHP5.  Assumes MySQL is already installed"""
    pass
    

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    # TODO config Portal
    pass

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    # TODO postinstall Portal
    pass

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify Portal

  def getRedeployArgs(self):
    """ Provide any command-line arguments to the installer on the slaves """
    # TODO: Return anything necessary.
    return []

