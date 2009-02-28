# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Pig

import logging
import os

from   com.cloudera.testutil.verbosetest import VerboseTestCase
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.functiontests.basetest as basetest
import distrotester.testproperties as testproperties


class PigTest(basetest.BaseTest):

  def getPigDir(self):
    return "/usr/lib/pig"

  def getPigCmd(self):
    return "/usr/bin/pig"


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
    # TODO(aaron): is the env script still relevant?
    envScript = os.path.join(self.getInstallRoot(), "user_env")
    runPig = self.getPigCmd()
    cmd = "source " + envScript + " && cd pig-tests && " \
        + self.getClientSudo() + runPig + " id.pig"
    shell.sh(cmd)

    logging.info("pig test appears to work!")


if __name__ == '__main__':
  unittest.main()

