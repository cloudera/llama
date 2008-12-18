# (c) Copyright 2008 Cloudera, Inc.
#
# Functionality unit test cases for Hadoop

from com.cloudera.testutil.asserts import TestCaseWithAsserts


class HadoopTest(TestCaseWithAsserts):

  def testHelloWorld(self):
    """ this is hello world """
    print "Hello, world!"

  def testTwo(self):
    """ another test """
    print "This is another hadoop test"



if __name__ == '__main__':
  unittest.main()

