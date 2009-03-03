# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Hive

import logging
import os

import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.functiontests.basetest as basetest
import distrotester.testproperties as testproperties


class HiveTest(basetest.BaseTest):

  def getHiveDir(self):
    return "/usr/lib/hive"

  def getHiveCmd(self):
    return "/usr/bin/hive"

  def getHiveWorkDir(self):
    """ Hive wants to write a bunch of files to the current directory.
     if we unzip this distro as root, we can't do that as the 'client'
     user. During setUp() we create a directory where the user can work
     from, and copy all the hive test files there. """

    clientUser = self.getProperties().getProperty(CLIENT_USER_KEY)
    hiveWorkDir = os.path.join(BASE_TMP_DIR, "hive-" + clientUser)
    return os.path.abspath(hiveWorkDir)


  def setUp(self):
    """ Perform setup tasks for tests """

    basetest.BaseTest.setUp(self)

    logging.debug("Setting up hive working environment")
    logging.debug("Current working dir: " + os.getcwd())

    hiveWorkDir = self.getHiveWorkDir()
    cmd = "mkdir -p " + hiveWorkDir
    shell.sh(cmd)

    cmd = "cp -r hive-tests/* " + hiveWorkDir
    shell.sh(cmd)

    clientUser = self.getProperties().getProperty(CLIENT_USER_KEY)
    cmd = "chown -R " + clientUser + ":" + clientUser + " " + hiveWorkDir
    shell.sh(cmd)

    # Make Hive tmp dir in HDFS.
    hadoop_user = self.getProperties().getProperty(HADOOP_USER_KEY)
    try:
      cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -mkdir /tmp"
      shell.sh(cmd)
    except:
      pass # ok for this to fail if dir already exists.

    cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -chmod +w /tmp"
    shell.sh(cmd)

    # Make Hive client user dir dir in HDFS.
    hadoop_user = self.getProperties().getProperty(HADOOP_USER_KEY)
    try:
      cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -mkdir /user/" + clientUser
      shell.sh(cmd)
    except:
      pass # ok for this to fail if dir already exists.

    cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -chown " + clientUser \
        + " /user/" + clientUser
    shell.sh(cmd)

    # Make Hive warehouse dir in HDFS.
    hadoop_user = self.getProperties().getProperty(HADOOP_USER_KEY)
    try:
      cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -mkdir /user/hive/warehouse"
      shell.sh(cmd)
    except:
      pass # ok for this to fail if dir already exists.

    cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -chmod +w /user/hive/warehouse"
    shell.sh(cmd)



  def testHiveInvitesQuery(self):
    """ Run the invites table example (create table, upload data, query) from
        http://wiki.apache.org/hadoop/Hive/GettingStarted
    """

    logging.info("Testing Hive Invites query...")
    # TODO(aaron): Is this still needed / relevant?
#    envScript = os.path.join(self.getInstallRoot(), "user_env")

    workDir = self.getHiveWorkDir()

    # Remove existing table if any.
    try:
      logging.debug("Removing existing invites table (if any)")
#      cmd = "source " + envScript + " && cd " + workDir + " && " \
      cmd = "cd " + workDir + " && " \
          + self.getClientSudo() + self.getHiveCmd() + " -f drop-invites.q"
      shell.sh(cmd)
    except shell.CommandError, ce:
      pass # table might not exist, so ignore the error

    # run the main test script.
    logging.debug("Running Hive query...")
#    cmd = "source " + envScript + " && cd " + workDir + " && " \
    cmd = "cd " + workDir + " && " \
        + self.getClientSudo() + self.getHiveCmd() + " -f invites.q"
    shell.sh(cmd)
    logging.debug("The Hive query appears to have succeeded!")


if __name__ == '__main__':
  unittest.main()

