# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.logmoverinstall
#
# Defines the ToolInstall instance that installs LogMover

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall

import com.cloudera.util.output as output
import com.cloudera.distribution.arch as arch

class LogMoverInstall(ToolInstall):
  def __init__(self, properties):
    ToolInstall.__init__(self, "LogMover", properties)


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    # TODO precheck LogMover
    pass

  def install(self):
    """ Run the installation itself. """

    # the log mover is only installed on the NN
    if self.isMaster():
      self.install_mysql()

    # TODO:
    # - install LogMover
    # - alter settings.py to use the proper Scribe log
    #   directories and to also have the correct
    #   GIT_ROOT.  settings.py must also have the
    #   correct HADOOP_HOME so it can speak with HDFS
    # - bootstrap MySQL with .sql files
    # - Setup cron jobs on NN only to run log movers
    # - Create the log directory specified in settings.py
    #   the logging module will break the script if the
    #   directory it is trying to log to doesn't exist

  def install_mysql(self):
    """Installs MySQL"""

    pckg = {arch.PACKAGE_MGR_DEBIAN: "mysql-server",
            arch.PACKAGE_MGR_RPM: "mysql-server",
            }
    self.installPackage(pckg)

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    # TODO config LogMover
    pass

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    # TODO postinstall LogMover
    pass

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify LogMover

  def getRedeployArgs(self):
    """ Provide any command-line arguments to the installer on the slaves """
    # TODO: Return anything necessary.
    return []

