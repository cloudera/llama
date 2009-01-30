# (c) Copyright 2009 Cloudera, Inc.
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

import pickle
import logging
import tempfile

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.constants import *

import com.cloudera.util.output as output
import com.cloudera.distribution.arch as arch
import com.cloudera.tools.shell as shell
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.tools.dirutils as dirutils

class LogMoverInstall(toolinstall.ToolInstall):

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

  def install(self):
    """ Run the installation itself. """

    # the log mover is only installed on the NN
    self.installLogMover()
    self.installLogFile()
    self.installConfigs()
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
      cmd = "cp -R " + logmover_src + " \"" + logmover_prefix + "\""
      shell.sh(cmd)
    except shell.CommandError:
      raise InstallError("Could not copy logmover files")
    except OSError, ose:
      raise InstallError("Could not copy logmover files: " + str(ose))

  def installLogFile(self):
    """ Create the file which the logmover expects to write, and
        set its permissions.
    """

    def make_file(filename):
      log_dir = os.path.dirname(filename)
      try:
        dirutils.mkdirRecursive(log_dir)
      except OSError, ose:
        raise InstallError("Could not create log directory: " + log_dir + " -- " + str(ose))

      try:
        # Open the log file, creating it if it doesn't already exist.
        handle = open(filename, "a")
        handle.close()
      except IOError, ioe:
        raise InstallError("Could not create log file: " + filename + " -- " + str(ioe))

      try:
        # chown/chmod it appropriately: the lighttpd user needs to read it,
        # and the logmover user (hadoop_user) needs to write it.
        cmd = "chmod 0644 \"" + filename + "\""
        shell.sh(cmd)

        hadoop_tool = toolinstall.getToolByName("Hadoop")
        hadoop_user = hadoop_tool.getHadoopUsername()
        cmd = "chown " + hadoop_user + " \"" + filename + "\""
        shell.sh(cmd)
      except shell.CommandError:
        raise InstallError("Could not set permissions for " + filename)

    make_file(LOG_MOVER_ERROR_FILE)
    make_file(LOG_MOVER_OFFSET_FILE)


  def installConfigs(self):
    """Install configuration files"""

    # grab the generic settings file location
    settings_gen = os.path.abspath(os.path.join(DEPS_PATH,
                                                "log_mover/settings.py"))

    logmover_prefix = self.getLogMoverPrefix()

    # copy the generic file in
    try:
      output.printlnVerbose("Copying over generic log mover settings file")
      cmd = "cp \"" + settings_gen + "\" \"" + logmover_prefix + "\""
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


  def installCronjob(self):
    """Installs the log mover cron job"""
    hadoop_tool = toolinstall.getToolByName("Hadoop")
    hadoop_user = hadoop_tool.getHadoopUsername()

    log_to_db = os.path.join(self.getInstallSymlinkPath("logmover"), "log_to_db.py")

    # Get their existing crontab and concatenate our new commands
    # on the end, don't just replace whatever they already have, completely.
    try:
      logging.debug("Retrieving existing crontab for " + hadoop_user)
      existing_cron = shell.shLines("crontab -l -u " + hadoop_user, False)
    except shell.CommandError:
      # somehow this failed? oh well, maybe it's just because there
      # was no such crontab. fail only on the case below where we
      # actually try to install a new crontab.
      logging.debug("Warning: could not read existing crontab")
      existing_cron = []

    # CH-125: check to make sure log_to_db isn't already there.
    need_log_to_db = True
    for existing_line in existing_cron:
      if existing_line.find("log_to_db.py") >= 0:
        logging.debug("Found existing cron job for log_to_db.py: " + existing_line.strip())
        need_log_to_db = False

    if need_log_to_db:
      existing_cron.append("* * * * * python " + log_to_db + "\n")
      # we changed something, so reinstall the cron.
      # put the new cron in a file to install
      try:
        (oshandle, tmpFilename) = tempfile.mkstemp("", "crontab-")
        handle = os.fdopen(oshandle, "w")
        for line in existing_cron:
          handle.write(line)
        handle.close()
      except OSError, ose:
        logging.error("Could not manipulate temporary file: " + str(ose))
      except IOError, ioe:
        logging.error("Could not write to temporary file: " + str(ioe))

      try:
        output.printlnVerbose("Attempting to install log mover cron")
        cmd = "crontab -u " + hadoop_user + " \"" + tmpFilename + "\""
        shell.sh(cmd)
      except shell.CommandError:
          raise InstallError("Unable to install the log mover cron job")

      # no longer need that temp file.
      try:
        os.remove(tmpFilename)
      except OSError, ose:
        logging.error("Warning: could not remove temporary file " + tmpFilename + ": " + str(ose))

    output.printlnInfo("Installed log mover cron job")


  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    pass


  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    # We only installed this on the head node; only start this on the same
    self.createInstallSymlink("logmover")
    self.createEtcSymlink("logmover", self.getFinalInstallPath())


  def verify(self):
    """ Run post-installation verification tests, if configured """
    pass


  def getRedeployArgs(self):
    """ Provide any command-line arguments to the installer on the slaves """
    return []


  def preserve_state(self, handle):
    pass # doesn't write any state.


  def restore_state(self, handle, role_list, version):
    self.role_list = role_list

    if version == "0.2.0":
      pass # no state preserved in this state :D
    else:
      raise InstallError("Cannot read state from file for version " + version)


