# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.logmoverinstall
#
# Defines the ToolInstall instance that installs LogMover

from   com.cloudera.distribution.installerror import InstallError
from com.cloudera.distribution.constants import *

import com.cloudera.util.output as output
import com.cloudera.distribution.arch as arch
import com.cloudera.tools.shell as shell
import com.cloudera.distribution.toolinstall as toolinstall

class LogMoverInstall(toolinstall.ToolInstall):
  def __init__(self, properties):
    toolinstall.ToolInstall.__init__(self, "LogMover", properties)

  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    pass

  def getLogMoverPrefix(self):
    """Gets the log mover install location"""
    install_prefix = self.properties.getProperty(INSTALL_PREFIX_KEY,
                                                 INSTALL_PREFIX_DEFAULT)

    # figure out where the log mover should be installed
    return os.path.join(install_prefix, "logmover")

  def install(self):
    """ Run the installation itself. """

    # the log mover is only installed on the NN
    if self.isMaster():
      self.installMysql()
      self.installLogMover()
      self.bootstrapMysql()
      self.installCronjob()

  def installLogMover(self):
    logmover_prefix = self.getLogMoverPrefix()

    # figure out which log mover files need to be copied
    logmover_src = os.path.abspath(os.path.join(LOGMOVER_PATH, "*"))

    try:
      output.printlnVerbose("Installing logmover from " + logmover_src)
      output.printlnInfo("Installing logmover to " + logmover_prefix)

      # create the install dir for the log mover
      os.mkdir(logmover_prefix)

      # copy the log mover over
      cmd = "cp -R " + logmover_src + " " + logmover_prefix
      cpLines = shell.shLines(cmd)
      output.printlnVerbose(cpLines)
    except shell.commandError:
      raise InstallError("Could not copy logmover files")

    self.installConfigs()

  def installConfigs(self):
    """Install configuration files"""

    # grab the generic settings file location
    settings_gen = os.path.abspath(os.path.join(DEPS_PATH,
                                                "log_mover/settings.py"))

    logmover_prefix = self.getLogMoverPrefix()

    # copy the generic file in
    try:
      output.printlnVerbose("Copying over generic log mover settings file")
      cmd = "cp " + settings_gen + " " + logmover_prefix
      cpLines = shell.shLines(cmd)
    except shell.CommandError:
      raise InstallError("Could not copy generic log mover settings file")

    # find the location of the generic settings file
    settings_loc = os.path.join(logmover_prefix, "settings.py")

    # sed the generic file so all appropriate pathes exist

    hadoop_home = self.properties.getProperty(INSTALL_PREFIX_KEY,
                                              INSTALL_PREFIX_DEFAULT)
    hadoop_home = os.path.join(hadoop_home, "hadoop")
    log_out = os.path.join(logmover_prefix, "logs")

    # TODO: ask aaron about this; I need to use his properties work
    scribe_logs = "scribe.logs"

    # escape the slashes in the path
    hadoop_home = hadoop_home.replace("/", "\\/")
    log_out = log_out.replace("/", "\\/")

    # set the $HADOOP_HOME var
    hadoop_cmd = "sed -i -e 's/path.to.hadoop.home/" + \
                             hadoop_home + "/' " + settings_loc
    # set the location where the log mover logs to
    log_cmd = "sed -i -e 's/path.to.log.dir/" + \
                             log_out + "/' " + settings_loc
    # set the location where scribe's logs are
    scribe_cmd = "sed -i -e 's/path.to.hadoop.scribe.logs/" + \
                             scribe_logs + "/' " + settings_loc

    # apply all the sed commands
    try:
      output.printlnVerbose("sedding the log mover settings file")

      lines = shell.shLines(hadoop_cmd)
      output.printlnVerbose(lines)

      lines = shell.shLines(log_cmd)
      output.printlnVerbose(lines)

      lines = shell.shLines(scribe_cmd)
      output.printlnVerbose(lines)
    except shell.CommandError:
      raise InstallError("Cannot configure the log mover settings file")

    output.printlnInfo("Done configuring log mover")

  def installMysql(self):
    """Installs MySQL"""

    pckg = {arch.PACKAGE_MGR_DEBIAN: ["mysql-server"],
            arch.PACKAGE_MGR_RPM: ["mysql-server"],
            }
    self.installPackage(pckg)

  def bootstrapMysql(self):
    """Bootstraps MySQL with the correct user, pasword, db, and schema"""
    logmover_prefix = self.getLogMoverPrefix()

    db_user = "root"
    use_passwd = not self.isUnattended()

    if not self.isUnattended():
      output.printlnInfo("""
Please provide your MySQL root password.  If you are unsure what your
root password is, then try not supplying a password.  Your MySQL root
password is required for creating a MySQL user for the log mover.
""")

    db_user_script = os.path.join(logmover_prefix, "db_user_and_db.sql")
    db_init_script = os.path.join(logmover_prefix, "db_init.sql")

    try:
      output.printlnVerbose("Attempting to bootstrap MySQL for the log mover")

      base_cmd = "mysql -u " + db_user + " "
      if use_passwd:
        base_cmd += "-p "
      cmd = base_cmd + "< " + db_user_script

      lines = shell.shLines(cmd)
      output.printlnVerbose(lines)

      if not self.isUnattended():
        output.printlnInfo("""
Please provide your MySQL root password again.  This time for
schema creation
""")

      cmd = base_cmd + "ana < " + db_init_script

      lines = shell.shLines(cmd)

      output.printlnVerbose(lines)
    except shell.CommandError:
      raise InstallError("Could not bootstrap MySQL with log mover schema and user")

    output.printlnInfo("Bootstrapped MySQL log mover schema and user")

  def installCronjob(self):
    hadoop_tool = toolinstall.getToolByName("Hadoop")
    hadoop_user = hadoop_tool.getHadoopUsername()

    pruner = os.path.join(self.getLogMoverPrefix(), "pruner.py")
    log_to_db = os.path.join(self.getLogMoverPrefix(), "log_to_db.py")

    try:
      output.printlnVerbose("Attempting to install log mover cron")
      cmd =  "echo '* * * * * " + pruner + "\n"
      cmd +=       "* * * * * " + log_to_db + "'"
      cmd += " | sudo crontab -u " + hadoop_user + " -"

      lines = shell.shLines(cmd)
      output.printlnVerbose(lines)
    except shell.CommandError:
      raise InstallError("Unable to install the log mover cron job")

    output.printlnInfo("Installed log mover cron job")

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    pass

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    # if we don't want to start any daemon processes, kill mysql
    if not self.mayStartDaemons():
      output.printlnVerbose("Attempting to stop mysql")
      arch_inst = arch.getArchDetector()
      try:
        # FC and Ubuntu have different init.d scripts
        cmd = ''
        if arch_inst.getPlatform() == arch.PLATFORM_UBUNTU:
          cmd = "/etc/init.d/mysql stop"
        elif arch_inst.getPlatform() == arch.PLATFORM_FEDORA:
          cmd = "/etc/init.d/mysqld stop"
        else:
          raise InstallError("Your Linux distribution is not supported")

        lines = shell.shLines(cmd)
        output.printlnVerbose(lines)
      except shell.CommandError:
        output.printlnInfo("Could not stop lighttpd")

      output.printlnInfo("Stopping lighttpd")

  def verify(self):
    """ Run post-installation verification tests, if configured """
    pass

  def getRedeployArgs(self):
    """ Provide any command-line arguments to the installer on the slaves """
    return []

