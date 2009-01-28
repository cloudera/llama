# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for LogMover

import logging
import os
import time

from   com.cloudera.testutil.verbosetest import VerboseTestCase
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties


class LogMoverTest(VerboseTestCase):

  def getInstallRoot(self):
    return INSTALL_PREFIX

  def getProperties(self):
    return testproperties.getProperties()

  def getHadoopDir(self):
    return os.path.join(INSTALL_PREFIX, "hadoop")

  def getHadoopCmd(self):
    return os.path.join(self.getHadoopDir(), "bin/hadoop")

  def getClientSudo(self):
    """ Return the shell cmd prefix to access a client hadoop program """
    clientUser = self.getProperties().getProperty(CLIENT_USER_KEY)

    if clientUser != ROOT_USER:
      return "sudo -H -u " + clientUser + " "
    else:
      return ""

  def getDaemonSudo(self):
    """ Return the shell cmd prefix to run a superuser hadoop program """
    superUser = self.getProperties().getProperty(HADOOP_USER_KEY)
    if superUser != ROOT_USER:
      return "sudo -H -u " + superUser + " "
    else:
      return ""


  def testNoRedundantCronJobs(self):
    " CH-125: scribe logmover should have exactly one entry in the crontab "

    hadoop_user = self.getProperties().getProperty(HADOOP_USER_KEY)
    logging.info("Checking crontab size for hadoop user: " + hadoop_user)
    cron_lines = shell.shLines("crontab -u " + hadoop_user + " -l")
    self.assertEqual(len(cron_lines), 1, "expected 1 crontab line")
    logging.info("crontab seems to be the correct size for hadoop user")


  def testLogMoverGetsError(self):
    """ Force an error to occur in the hadoop log. Check to make sure
        the logmover picks it up and sends it to the errors file.
    """

    logging.info("Testing logmover...")

    daemon_script = os.path.join(self.getHadoopDir(), "bin/hadoop-daemon.sh")

    # first, shut down any datanode currently on this host
    try:
      cmd = self.getDaemonSudo() + daemon_script + " stop datanode"
      shell.sh(cmd)
    except shell.CommandError:
      pass # no daemon to stop - not a problem

    try:
      # use a deprecated argument to start datanode which ensures that an
      # ERROR is logged
      cmd = self.getDaemonSudo() + daemon_script + " start datanode -r /foo"
      shell.sh(cmd)
    except shell.CommandError:
      pass # this command is supposed to fail.

    logging.info("Log entry recorded. Waiting for logmover results...")

    # now check our log file for this message.
    startTime = time.time()

    # We give ourselves up to 90 seconds to find the entry
    # (the log mover runs on a cron every 60 seconds.)
    TARGET_LOG_FILE = "/var/log/hadoop/errors"
    TIMEOUT = 90
    found = False
    while time.time() - startTime < TIMEOUT and not found:
      handle = open(TARGET_LOG_FILE)
      lines = handle.readlines()
      handle.close()

      if len(lines) > 0:
        found = True
      else:
        logging.info("Waiting for data...")
        time.sleep(5)

    if found:
      logging.info("LogMover appears to propagate logs correctly!")
    else:
      self.fail("Couldn't find error entry in log; logmover appears to not work")




if __name__ == '__main__':
  unittest.main()

