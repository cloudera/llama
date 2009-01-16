# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Pig

import logging
import os

from   com.cloudera.testutil.asserts import TestCaseWithAsserts
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties


class PigTest(TestCaseWithAsserts):

  def getInstallRoot(self):
    return INSTALL_PREFIX

  def getProperties(self):
    return testproperties.getProperties()

  def getPigDir(self):
    return os.path.join(INSTALL_PREFIX, "pig")

  def getPigCmd(self):
    return os.path.join(self.getPigDir(), "bin/pig")

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


  def setUp(self):
    """ Perform setup tasks for tests """

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


  def testPigPasswdId(self):
    """ Run the passwd id example from http://wiki.apache.org/pig/RunPig """
    # This runs on the Hadoop cluster, not in local mode.

    logging.info("Resetting pig HDFS state")

    # Remove existing in-HDFS files related to this test.
    try:
      cmd = self.getClientSudo() + self.getHadoopCmd() + " fs -rm passwd-data"
      shell.sh(cmd)
    except shell.CommandError, ce:
      pass # file might not exist, so ignore the error

    try:
      cmd = self.getClientSudo() + self.getHadoopCmd() + " fs -rmr id.out"
      shell.sh(cmd)
    except shell.CommandError, ce:
      pass # dir might not exist, so ignore the error

    # Copy passwd-data up to HDFS
    cmd = self.getClientSudo() + self.getHadoopCmd() + " fs -put " \
        + "pig-tests/passwd-data passwd-data"
    shell.sh(cmd)

    logging.info("Running pig script...")

    # run the id.pig script
    envScript = os.path.join(self.getInstallRoot(), "user_env")
    runPig = self.getPigCmd()
    cmd = "source " + envScript + " && cd pig-tests && " \
        + self.getClientSudo() + runPig + " id.pig"
    shell.sh(cmd)

    logging.info("pig test appears to work!")


if __name__ == '__main__':
  unittest.main()

