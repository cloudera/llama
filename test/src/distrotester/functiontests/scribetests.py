# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Scribe

import logging
import os
import time

import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.functiontests.basetest as basetest
import distrotester.testproperties as testproperties


class ScribeTest(basetest.BaseTest):

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
    SCRIBE_TIMEOUT = 30 # give ourselves up to 30 seconds to find the nonce
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

