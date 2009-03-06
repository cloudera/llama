# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Hadoop

import logging
import os

import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.functiontests.basetest as basetest
import distrotester.testproperties as testproperties


class HadoopTest(basetest.BaseTest):

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


  def testBzipMR(self):
    """ Run a job which writes out numbers to a text file, compresses this in a
        SequenceFile with bzip2, sorts that file, and confirms that the same
        numbers come back.
    """

    logging.info("Testing bzip2 / SequenceFile integration...")
    cmd = self.getClientSudo() + self.getHadoopCmd() + " jar " \
        + self.getTestJar() + " com.cloudera.bzip2test.BzipDriver"
    try:
      shell.sh(cmd)
      logging.info("SequenceFile + bzip2 integration seems to work!")
    except shell.CommandError:
      self.fail("BZip2/SequenceFile MapReduce exited with non-zero status.")


if __name__ == '__main__':
  unittest.main()

