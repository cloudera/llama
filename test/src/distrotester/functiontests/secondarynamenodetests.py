# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Hadoop SecondaryNameNode
# This is intended to be used with the fast-checkpoint.xml test configuration.

import logging
import os
import time

import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.functiontests.basetest as basetest
import distrotester.testproperties as testproperties


# This is the number of seconds in the hadoop-site.xml config between 2NN syncs.
CHECKPOINT_INTERVAL = 10


class SecondaryNameNodeTest(basetest.BaseTest):

  def wait_for_dfs_daemons(self):
    """
        This test will have been run in the context of one of the install
        tests, which also should have started all the appropriate hadoop
        daemons. Wait for safemode to exit and at least one checkpoint interval.
    """

    # Wait for safemode off
    logging.debug("Waiting for safemode to exit...")
    safemode_cmd = self.getDaemonSudo() + self.getHadoopCmd() + " dfsadmin -safemode wait"
    shell.sh(safemode_cmd)
    logging.debug("Safemode is over!")

    # Sleep for twice the checkpoint interval
    logging.debug("Waiting for initial checkpoint sync...")
    time.sleep(2 * CHECKPOINT_INTERVAL)
    logging.debug("Wait complete.")


  def testSecondaryViaScribe(self):
    """ Test that the secondarynamenode-specific log lines are being added
        to our scribe logs.
    """

    self.wait_for_dfs_daemons()

    # The 2NN should have snapshotted by now. See if it is in the scribe log.
    magic_string = "NameNode.Secondary: Downloaded file fsimage"

    # grep will exit with 0 if it finds the string, and 1 if it doesn't.
    logging.debug("Searching scribe logs for string: " + magic_string)
    cmd = "grep -R \"" + magic_string + "\" " + SCRIBE_OUT_DIR
    try:
      shell.sh(cmd)
    except shell.CommandError:
      self.fail("Could not find 2NN activity signature in scribe log")

    logging.debug("SecondaryNameNode appears to be writing to Scribe.")


  def testSecondaryMd5Change(self):
    """ Examine the contents of the 2nn's directory, and verify that data
        changes if we change metadata in hdfs.
    """

    global CHECKPOINT_INTERVAL
    global CHECKPOINT_DIR

    self.start_dfs_daemons()

    secondary_server = self.getProperties().getProperty(SECONDARY_HOSTNAME_KEY)
    logging.debug("Secondary server addr is: " + secondary_server)

    # The 2NN should now have a static snapshot.
    # Grab the md5sum of the 'edits' and 'fsimage' files.
    logging.debug("Grabbing edits file md5")
    edits_file = os.path.join(CHECKPOINT_DIR, "current/edits")
    editsLines = shell.sshLines("root", secondary_server, "md5sum " + edits_file, \
        self.getProperties())
    if len(editsLines) > 0:
      initial_edit_md5 = editsLines[0]
    else:
      self.fail("Couldn't read md5sum for edits file in round 1")

    fsimage_file = os.path.join(CHECKPOINT_DIR, "current/fsimage")
    logging.debug("Grabbing fsimage md5")
    fsimageLines = shell.sshLines("root", secondary_server, "md5sum " + fsimage_file, \
        self.getProperties())
    if len(fsimageLines) > 0:
      initial_fsimage_md5 = fsimageLines[0]
    else:
      self.fail("Couldn't read md5sum for fsimage file in round 1")


    logging.debug("Updating filesystem metadata to detect change")

    # Now change the filesystem metadata.
    nonce_filename = "nonce_file_" + str(time.time())
    safemode_cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -touchz " + nonce_filename

    # Now wait for 2 checkpoint intervals again.
    logging.debug("Waiting for checkpoint to synchronize...")
    time.sleep(2 * CHECKPOINT_INTERVAL)
    logging.debug("Wait complete")

    # Now re-query the md5sums. At least one of these should be different.
    logging.debug("Grabbing edits file md5")
    editsLines = shell.sshLines("root", secondary_server, "md5sum " + edits_file, \
        self.getProperties())
    if len(editsLines) > 0:
      second_edit_md5 = editsLines[0]
    else:
      self.fail("Couldn't read md5sum for edits file in round 2")

    logging.debug("Grabbing fsimage md5")
    fsimageLines = shell.sshLines("root", secondary_server, "md5sum " + fsimage_file, \
        self.getProperties())
    if len(fsimageLines) > 0:
      second_fsimage_md5 = fsimageLines[0]
    else:
      self.fail("Couldn't read md5sum for fsimage file in round 2")

    if second_fsimage_md5 == initial_fsimage_md5 and second_edits_md5 == initial_edits_md5:
      self.fail("md5 sums have stayed the same over the checkpoint interval - fail!")

    logging.debug("md5 sums have been updated. SecondaryNameNode appears to work.")




if __name__ == '__main__':
  unittest.main()

