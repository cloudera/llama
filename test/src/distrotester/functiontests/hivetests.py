# (c) Copyright 2008 Cloudera, Inc.
#
# Functionality unit test cases for Hive

import logging
import os

from   com.cloudera.testutil.asserts import TestCaseWithAsserts
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties


class HiveTest(TestCaseWithAsserts):

  def getInstallRoot(self):
    return INSTALL_PREFIX

  def getProperties(self):
    return testproperties.getProperties()

  def getHiveDir(self):
    return os.path.join(INSTALL_PREFIX, "hive")

  def getHiveCmd(self):
    return os.path.join(self.getHiveDir(), "bin/hive")

  # TODO(aaron): Refactor getHadoopDir, Cmd, get*Sudo into common abstract base
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

    # TODO(aaron): user dir creation is in Pig and Hive (also Hadoop)Test?
    # Refactor out.

    # Ensure that the user's home dir exists in HDFS
    clientUser = self.getProperties().getProperty(CLIENT_USER_KEY)
    cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -mkdir /user/" \
        + clientUser
    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      pass # ok for this to cause error (if dir already exists)

    cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -chown " \
        + clientUser + " /user/" + clientUser
    shell.sh(cmd)


    hiveWorkDir = self.getHiveWorkDir()
    cmd = "mkdir -p " + hiveWorkDir
    shell.sh(cmd)

    cmd = "cp -r hive-tests/* " + hiveWorkDir
    shell.sh(cmd)

    cmd = "chown -R " + clientUser + ":" + clientUser + " " + hiveWorkDir
    shell.sh(cmd)



  def testHiveInvitesQuery(self):
    """ Run the invites table example (create table, upload data, query) from
        http://wiki.apache.org/hadoop/Hive/GettingStarted
    """

    logging.info("Testing Hive Invites query...")
    envScript = os.path.join(self.getInstallRoot(), "user_env")

    workDir = self.getHiveWorkDir()

    # Remove existing table if any.
    try:
      logging.debug("Removing existing invites table (if any)")
      cmd = "source " + envScript + " && cd " + workDir + " && " \
          + self.getClientSudo() + self.getHiveCmd() + " -f drop-invites.q"
      shell.sh(cmd)
    except shell.CommandError, ce:
      pass # table might not exist, so ignore the error

    # run the main test script.
    logging.debug("Running Hive query...")
    cmd = "source " + envScript + " && cd " + workDir + " && " \
        + self.getClientSudo() + self.getHiveCmd() + " -f invites.q"
    shell.sh(cmd)
    logging.debug("The Hive query appears to have succeeded!")


if __name__ == '__main__':
  unittest.main()

