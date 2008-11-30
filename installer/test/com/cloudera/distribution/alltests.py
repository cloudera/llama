#!/usr/bin/python
#
# (c) Copyright 2008 Cloudera, Inc.
#
# AllTests for the distribution installer

import sys
import unittest

from com.cloudera.distribution.dnsregextest import DnsRegexTest

def testSuite():
  dnsRegexSuite = unittest.makeSuite(DnsRegexTest, 'test')

  alltests = unittest.TestSuite([dnsRegexSuite])
  return alltests

if __name__ == "__main__":
  runner = unittest.TextTestRunner()
  sys.exit(not runner.run(testSuite()).wasSuccessful())
