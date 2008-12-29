# (c) Copyright 2008 Cloudera, Inc.
#
# Functionality unit test cases for Scribe

import logging
import os
import time

from   com.cloudera.testutil.asserts import TestCaseWithAsserts
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties


class ScribeTest(TestCaseWithAsserts):

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

  def getTestJar(self):
    """ Return the filename of the jar containing the Java code to run """
    return SCRIBE_TEST_JAR


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


  def testScribeReceivesLogEntry(self):
    """ Run a MapReduce job that produces a log entry in scribe. We then
        check the logs on the master server to make sure it appears. """

    logging.info("Testing scribe...")

    nonce = "nonce-" + str(time.time())
    cmd = self.getClientSudo() + self.getHadoopCmd() + " jar " \
        + self.getTestJar() + " com.cloudera.scribetest.ScribeDriver " \
        + nonce

    logging.info("Running MapReduce log job with nonce: " + nonce)
    shell.sh(cmd)
    logging.info("MapReduce job finished. Waiting for log results...")

    # now check our log file for this nonce.
    startTime = time.time()

    # TODO(aaron): If the hadoop_current symlink gets switched, we won't know
    # this. We need a more thorough means of scanning *all* the logs in this
    # directory. (CH-78)

    TARGET_LOG_FILE = "/var/log/scribe/central/hadoop/hadoop_current"
    SCRIBE_TIMEOUT = 120 # give ourselves up to 2 minutes to find the nonce
    found = False
    handle = open(TARGET_LOG_FILE)
    while time.time() - startTime < SCRIBE_TIMEOUT and not found:
      line = handle.readline()
      if len(line) == 0:
        # We overran the end of the file; wait for scribe to give us more data
        logging.info("Waiting for more data...")
        time.sleep(1)
      else:
        try:
          line.index(nonce)
          found = True
        except ValueError:
          # Couldn't find the nonce in this line.
          pass

    handle.close()

    if found:
      logging.info("Scribe appears to propagate logs correctly!")
    else:
      self.fail("Couldn't find nonce in logs; scribe appears to not work")




if __name__ == '__main__':
  unittest.main()

