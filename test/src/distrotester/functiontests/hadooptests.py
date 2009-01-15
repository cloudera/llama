# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Hadoop

import logging
import os

from   com.cloudera.testutil.asserts import TestCaseWithAsserts
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties


class HadoopTest(TestCaseWithAsserts):

  def getHadoopDir(self):
    return os.path.join(INSTALL_PREFIX, "hadoop")

  def getProperties(self):
    return testproperties.getProperties()

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


  def testCreateFile(self):
    """ create a file in HDFS and ensure that it exists """

    # Create the file
    cmd = self.getClientSudo() + self.getHadoopCmd() \
        + " fs -touchz SomeFileName"
    shell.sh(cmd)

    cmd = self.getClientSudo() + self.getHadoopCmd() + " fs -ls"
    lines = shell.shLines(cmd)
    found = False
    for line in lines:
      try:
        line.index("SomeFileName")
        logging.debug("Found touchz-created file")
        found = True
      except ValueError:
        pass

    if not found:
      self.fail("Could not find HDFS file we created")


  def testPiMapReduce(self):
    """ Run the pi example """

    exampleJar = os.path.join(self.getHadoopDir(), "hadoop-*-examples.jar")
    cmd = self.getClientSudo() + self.getHadoopCmd() + " jar " \
        + exampleJar + " pi 3 50000"
    lines = shell.shLines(cmd)
    numLines = len(lines)
    found = False
    if numLines > 0:
      for line in lines:
        if line.startswith("Estimated value of PI is"):
          logging.debug("PI calculator seems to have exited successfully.")
          found = True
    if not found:
      self.fail("Could not run pi example to completion")


if __name__ == '__main__':
  unittest.main()

