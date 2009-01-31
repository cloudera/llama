# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Hadoop SecondaryNameNode

import logging
import os
import time

from   com.cloudera.testutil.verbosetest import VerboseTestCase
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties

# This is intended to be used with the fast-checkpoint.xml test configuration.

# This is the number of seconds in the hadoop-site.xml config between 2NN syncs.
CHECKPOINT_INTERVAL = 10



__secondary_server = None
def setSecondaryServer(server_addr):
  """ Sets the address of the 2NN which should be used for this unit test. """
  global __secondary_server

  __secondary_server = server_addr


class SecondaryNameNodeTest(VerboseTestCase):

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

  def start_dfs_daemons(self):
    """ Start HDFS and wait for safemode to exit and at least one checkpoint
        interval
    """

    # Ensure that all HDFS services are started.
    logging.debug("Ensuring all HDFS services are started...")
    start_daemon_cmd = os.path.join(self.getHadoopDir(), "bin/start-dfs.sh")
    cmd = self.getDaemonSudo() + start_daemon_cmd
    shell.sh(cmd)

    # Wait for safemode off
    logging.debug("Waiting for safemode to exit...")
    safemode_cmd = self.getDaemonSudo() + getHadoopCmd + " dfsadmin -safemode wait"
    shell.sh(safemode_cmd)
    logging.debug("Safemode is over!")

    # Sleep for twice the checkpoint interval
    logging.debug("Waiting for initial checkpoint sync...")
    time.sleep(2 * CHECKPOINT_INTERVAL)


  def testSecondaryViaScribe(self):
    """ Test that the secondarynamenode-specific log lines are being added
        to our scribe logs.
    """

    self.start_dfs_daemons()

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

    global __secondary_server
    global CHECKPOINT_INTERVAL
    global CHECKPOINT_DIR

    self.start_dfs_daemons()

    # The 2NN should now have a static snapshot.
    # Grab the md5sum of the 'edits' and 'fsimage' files.
    edits_file = os.path.join(CHECKPOINT_DIR, "edits")
    editsLines = shell.sshLines("root", __secondary_server, "md5sum " + edits_file)
    if len(editsLines) > 0:
      initial_edit_md5 = editsLines[0]
    else:
      self.fail("Couldn't read md5sum for edits file in round 1")

    fsimage_file = os.path.join(CHECKPOINT_DIR, "fsimage")
    fsimageLines = shell.sshLines("root", __secondary_server, "md5sum " + fsimage_file)
    if len(fsimageLines) > 0:
      initial_fsimage_md5 = fsimageLines[0]
    else:
      self.fail("Couldn't read md5sum for fsimage file in round 1")


    # Now change the filesystem metadata.
    nonce_filename = "nonce_file_" + str(time.time())
    safemode_cmd = self.getDaemonSudo() + getHadoopCmd + " fs -touchz " + nonce_filename

    # Now wait for 2 checkpoint intervals again.
    logging.debug("Waiting for checkpoint to synchronize...")
    time.sleep(2 * CHECKPOINT_INTERVAL)

    # Now re-query the md5sums. At least one of these should be different.
    editsLines = shell.sshLines("root", __secondary_server, "md5sum " + edits_file)
    if len(editsLines) > 0:
      second_edit_md5 = editsLines[0]
    else:
      self.fail("Couldn't read md5sum for edits file in round 2")

    fsimageLines = shell.sshLines("root", __secondary_server, "md5sum " + fsimage_file)
    if len(fsimageLines) > 0:
      second_fsimage_md5 = fsimageLines[0]
    else:
      self.fail("Couldn't read md5sum for fsimage file in round 2")

    if second_fsimage_md5 == initial_fsimage_md5 and second_edits_md5 == initial_edits_md5:
      self.fail("md5 sums have stayed the same over the checkpoint interval - fail!")

    logging.debug("md5 sums have been updated. SecondaryNameNode appears to work.")




if __name__ == '__main__':
  unittest.main()

