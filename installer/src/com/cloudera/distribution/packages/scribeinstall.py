# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.Scribeinstall
#
# Defines the ToolInstall instance that installs Scribe

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.util.output as output


class ScribeInstall(ToolInstall):
  def __init__(self, properties):
    ToolInstall.__init__(self, "Scribe", properties)

    self.addDependency("LogMover")
    self.addDependency("Portal")

  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    # TODO precheck Scribe
    pass

  def install(self):
    """ Run the installation itself. """
    pass
    # TODO:
    # - install Scribe and dependencies (specified below)
    # - modify log4j.xml as to allow for the Scribe appender and also
    #   to have the node's hostname added
    # - add Scribe appender and libraries to HADOOP_CLASSPATH
    #    -- do this by just appending hadop-site.env
    # - install configuration files

    # TODO: tell the user how to start Scribe
    # TODO: tell the user to add LD_LIBRARY_PATH to their env

    # dependencies:
    #  -python
    #  -python-dev
    #  -ruby
    #  -libevent-dev

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    # TODO config Scribe
    pass

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    # TODO postinstall Scribe
    pass

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify Scribe

  def getRedeployArgs(self):
    """ Provide any command-line arguments to the installer on the slaves """
    # TODO: Return anything necessary.
    return []

