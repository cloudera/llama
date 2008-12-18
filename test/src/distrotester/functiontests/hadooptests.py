# (c) Copyright 2008 Cloudera, Inc.
#
# Functionality unit test cases for Hadoop

import os

from   com.cloudera.testutil.asserts import TestCaseWithAsserts
import com.cloudera.tools.shell as shell

from   distrotester.constants import *


class HadoopTest(TestCaseWithAsserts):

  def getHadoopDir(self):
    return os.path.join(INSTALL_PREFIX, "hadoop")

  def getHadoopCmd(self):
    return os.path.join(self.getHadoopDir(), "bin/hadoop")


  def testCreateFile(self):
    """ create a file in HDFS and ensure that it exists """

    # Create the file
    cmd = self.getHadoopCmd() + " fs -touchz SomeFileName"
    shell.sh(cmd)

    cmd = self.getHadoopCmd() + " fs -ls"
    lines = shell.shLines(cmd)
    found = False
    for line in lines:
      try:
        line.index("SomeFileName")
        found = True
      except ValueError:
        pass

    if not found:
      self.fail("Could not find HDFS file we created")


  def testPiMapReduce(self):
    """ Run the pi example """

    exampleJar = os.path.join(self.getHadoopDir(), "hadoop-*-examples.jar")
    cmd = self.getHadoopCmd() + " jar " + exampleJar + " pi 3 30000"
    lines = shell.shLines(cmd)
    numLines = len(lines)
    found = False
    if numLines > 0:
      for line in lines:
        if line.startswith("Estimated value of PI is"):
          found = True
    if not found:
      self.fail("Could not run pi example to completion")


if __name__ == '__main__':
  unittest.main()

