# (c) Copyright 2008 Cloudera, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# module: com.cloudera.distribution.packages.logmoverinstall
#
# Defines the ToolInstall instance that installs LogMover

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.constants import *

import com.cloudera.util.output as output
import com.cloudera.distribution.arch as arch
import com.cloudera.tools.shell as shell
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.tools.dirutils as dirutils

class LogMoverInstall(toolinstall.ToolInstall):

  # the backup file (if any) of the .my.cnf file
  myCnfBackup = None

  def __init__(self, properties):
    toolinstall.ToolInstall.__init__(self, "LogMover", properties)

  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    pass

  def getLogMoverPrefix(self):
    """Gets the log mover install location"""
    install_prefix = self.getInstallBasePath()

    # figure out where the log mover should be installed
    return os.path.join(install_prefix, "logmover")

  def getFinalInstallPath(self):
    return self.getLogMoverPrefix()

  def getSuperUserPasswd(self):
    return self.properties.getProperty(DB_SUPERUSER_PASSWD_KEY,
                                       DB_SUPERUSER_PASSWD_DEFAULT)

  def install(self):
    """ Run the installation itself. """

    # the log mover is only installed on the NN
    if self.isMaster():
      self.installMysql()
      self.installLogMover()
      self.installConfigs()
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
      dirutils.mkdirRecursive(logmover_prefix)

      # copy the log mover over
      cmd = "cp -R " + logmover_src + " " + logmover_prefix
      shell.sh(cmd)
    except shell.CommandError:
      raise InstallError("Could not copy logmover files")
    except OSError, ose:
      raise InstallError("Could not copy logmover files: " + str(ose))

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
      shell.sh(cmd)
    except shell.CommandError:
      raise InstallError("Could not copy generic log mover settings file")

    # find the location of the generic settings file
    settings_loc = os.path.join(logmover_prefix, "settings.py")

    # sed the generic file so all appropriate pathes exist

    hadoop_home = toolinstall.getToolByName("Hadoop").getFinalInstallPath()
    log_out = os.path.join(logmover_prefix, "logs")

    # get the location of the scribe logs
    scribe_installer = toolinstall.getToolByName("Scribe")
    scribe_logs = scribe_installer.getScribeLogDir()

    # we want the log mover to look at central logs for hadoop
    scribe_logs = os.path.join(scribe_logs, "central/hadoop")

    # set the $HADOOP_HOME var
    hadoop_cmd = "sed -i -e 's|path.to.hadoop.home|" + \
                             hadoop_home + "|' " + settings_loc
    # set the location where the log mover logs to
    log_cmd = "sed -i -e 's|path.to.log.dir|" + \
                             log_out + "|' " + settings_loc
    # set the location where scribe's logs are
    scribe_cmd = "sed -i -e 's|path.to.hadoop.scribe.logs|" + \
                             scribe_logs + "|' " + settings_loc

    # apply all the sed commands
    try:
      output.printlnVerbose("sedding the log mover settings file")

      shell.sh(hadoop_cmd)
      shell.sh(log_cmd)
      shell.sh(scribe_cmd)
    except shell.CommandError:
      raise InstallError("Cannot configure the log mover settings file")

    # lastly, make sure the log_out folder exists, so the log mover
    # logging framework won't complain
    try:
      output.printlnVerbose("Attempting to create the log mover log dir")
      dirutils.mkdirRecursive(log_out)
      cmd = "chown hadoop -R " + log_out
      shell.sh(cmd)
    except:
      raise InstallError("Couldn't create log mover's log directory")

    output.printlnInfo("Done configuring log mover")

  def installMysql(self):
    """Installs MySQL and required modules"""

    pckg = {arch.PACKAGE_MGR_DEBIAN: ["mysql-server",
                                      "python-mysqldb"],
            arch.PACKAGE_MGR_RPM: ["mysql-server",
                                   "MySQL-python"],
            }
    self.installPackage(pckg)

  def bootstrapMysql(self):
    """Bootstraps MySQL with the correct user, pasword, db, and schema"""
    logmover_prefix = self.getLogMoverPrefix()

    db_user = DB_SUPERUSER
    use_passwd = not self.isUnattended() or \
                 (self.isUnattended() and self.getSuperUserPasswd() != "")

    if self.isUnattended() and self.getSuperUserPasswd() != "":
      self.createMyCnf()

    db_user_script = os.path.join(logmover_prefix, "db_user_and_db.sql")
    db_init_script = os.path.join(logmover_prefix, "db_init.sql")

    output.printlnVerbose("Attempting to bootstrap MySQL for the log mover")

    # make sure MySQL is running
    mysql_map = {arch.PLATFORM_UBUNTU: "/etc/init.d/mysql",
                 arch.PLATFORM_FEDORA: "/etc/init.d/mysqld"
                 }
    state = "start"

    output.printlnVerbose("Making sure MySQL is already running")

    self.modifyDaemon(mysql_map, state)

    if not self.isUnattended():
      output.printlnInfo("""
Please provide your MySQL root password.  If you are unsure what your
root password is, then try not supplying a password.  Your MySQL root
password is required for creating a MySQL user for the log mover.
""")

    try:
      base_cmd = "mysql -u " + db_user + " "

      # pass the -p param if we are using a password
      if use_passwd:
        base_cmd += "-p "

      cmd = base_cmd + "< " + db_user_script

      shell.sh(cmd)

      if not self.isUnattended():
        output.printlnInfo("""
Please provide your MySQL root password again.  This time for
schema creation
""")

      cmd = base_cmd + LOGMOVER_DB_NAME + " < " + db_init_script

      shell.sh(cmd)
    except shell.CommandError:
      raise InstallError("Could not bootstrap MySQL with log mover schema and user")

    if self.isUnattended() and self.getSuperUserPasswd() != "":
      self.destroyMyCnf()

    output.printlnInfo("Bootstrapped MySQL log mover schema and user")

  def createMyCnf(self):
    """Create the /root/.my.cnf file so we can login to mysql as root"""

    output.printlnVerbose("Creating .my.cnf")

    if os.path.exists(ROOT_MY_CNF_FILE):
      self.myCnfBackup = toolinstall.ToolInstall.backupFile(ROOT_MY_CNF_FILE)
      output.printlnVerbose(".my.cnf exists, backing up to " + self.myCnfBackup)

    fh = open(ROOT_MY_CNF_FILE, 'w')
    fh.write("[client]\n")
    fh.write("password="+self.getSuperUserPasswd())
    fh.close()

    try:
      cmd = "chmod 600 " + ROOT_MY_CNF_FILE
      shell.sh(cmd)
    except shell.CommandError:
      raise InstallError("Could not change the permissions on the .my.cnf file")

  def destroyMyCnf(self):
    """Delete the /root/.my.cnf file created in createMyCnf()"""

    output.printlnVerbose("Attempting to remote the mycnf file")

    cmd = ""
    # if a backup was created, move the backup back in place
    if self.myCnfBackup != None:
      cmd = "mv " + self.myCnfBackup + " " + ROOT_MY_CNF_FILE
      output.printlnVerbose("Restoring .my.cnf backup")
    # if a backup wasn't created, just delete the file
    else:
      cmd = "rm " + ROOT_MY_CNF_FILE

    try:
      shell.sh(cmd)
    except shell.CommandError:
      raise InstallError("Unable to clean up .my.cnf")

  def installCronjob(self):
    """Installs the log mover cron job"""
    hadoop_tool = toolinstall.getToolByName("Hadoop")
    hadoop_user = hadoop_tool.getHadoopUsername()

    pruner = os.path.join(self.getLogMoverPrefix(), "prune.py")
    log_to_db = os.path.join(self.getLogMoverPrefix(), "log_to_db.py")

    try:
      output.printlnVerbose("Attempting to install log mover cron")
      cmd =  "echo '* * * * * python " + pruner + "\n"
      cmd +=       "* * * * * python " + log_to_db + "'"
      cmd += " | sudo crontab -u " + hadoop_user + " -"

      shell.sh(cmd)
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

    if self.isMaster():
      # We only installed this on the head node; only start this on the same
      self.createInstallSymlink("logmover")
      self.createEtcSymlink("logmover", self.getFinalInstallPath())

      # if we don't want to start any daemon processes, kill mysql
      if not self.mayStartDaemons():
        mysql_map = {arch.PLATFORM_UBUNTU: "/etc/init.d/mysql",
                     arch.PLATFORM_FEDORA: "/etc/init.d/mysqld"
                     }

        lighttpd_map = {arch.PLATFORM_UBUNTU: "/etc/init.d/lighttpd",
                        arch.PLATFORM_FEDORA: "/etc/init.d/lighttpd"
                         }

        state = "stop"

        self.modifyDaemon(mysql_map, state)
        self.modifyDaemon(lighttpd_map, state)

  def verify(self):
    """ Run post-installation verification tests, if configured """
    pass

  def getRedeployArgs(self):
    """ Provide any command-line arguments to the installer on the slaves """
    return []

