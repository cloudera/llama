# (c) Copyright 2008 Cloudera, Inc.
#
# module: distrotester.installtests.standalone
#
# Functionality unit tests for launching the installer on a single-node
# (standalone) "cluster"

import unittest

from com.cloudera.testutil.asserts import TestCaseWithAsserts

from distrotester.functiontests.hadooptests import HadoopTest

class StandaloneTest(TestCaseWithAsserts):

  def setUp(self):
    print "Setup: Remove any existing hadoop distro, etc."

  def testHadoopOnly(self):
    """ Install *only* the hadoop component. """

    print "TODO: Install hadoop here!"

    hadoopSuite = unittest.makeSuite(HadoopTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()



