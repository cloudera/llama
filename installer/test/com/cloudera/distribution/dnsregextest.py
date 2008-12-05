#!/usr/bin/env python
# (c) Copyright 2008 Cloudera, Inc.
#
# Unit test cases for dns-matching regular expressions
# used by HadoopInstaller to validate whether addresses are legal

from com.cloudera.testutil.asserts import TestCaseWithAsserts

import com.cloudera.distribution.dnsregex as dnsregex

class DnsRegexTest(TestCaseWithAsserts):

  ### tests for IP address resolution ###

  def testEmptyIpAddrNoMatch(self):
    self.assertFalse(dnsregex.isIpAddress(""))

  def testInt1Matches(self):
    self.assertTrue(dnsregex.isIpAddress("1"))

  def testInt3Matches(self):
    self.assertTrue(dnsregex.isIpAddress("123"))

  def testInt9Matches(self):
    self.assertTrue(dnsregex.isIpAddress("123456789"))

  def testInt10NoMatch(self):
    self.assertTrue(dnsregex.isIpAddress("1234567890"))

  def testDottedHalfMatch(self):
    self.assertTrue(dnsregex.isIpAddress("1234.5678"))

  def testIntDotNoMatch(self):
    self.assertFalse(dnsregex.isIpAddress("1234."))

  def testDottedQuadsMatch(self):
    self.assertTrue(dnsregex.isIpAddress("123.123.123.123"))

  def testAlphaNoMatch(self):
    self.assertFalse(dnsregex.isIpAddress("123.1A2.123.123"))

  def testAlphaNoMatch2(self):
    self.assertFalse(dnsregex.isIpAddress("A12.123.123.123"))

  def testAlphaNoMatch3(self):
    self.assertFalse(dnsregex.isIpAddress("12.123.123.12a"))

  def testFiveQuadsNoMatch(self):
    self.assertFalse(dnsregex.isIpAddress("1.2.3.4.5"))

  def testFourQuadsDotNoMatch(self):
    self.assertFalse(dnsregex.isIpAddress("1.2.3.4."))

  def testMultiDotsNotIp(self):
    self.assertFalse(dnsregex.isIpAddress("123..123"))

  def testOneDotNotIp(self):
    self.assertFalse(dnsregex.isIpAddress("."))

  def testTwoDotsNotIp(self):
    self.assertFalse(dnsregex.isIpAddress(".."))

  def testThreeDotsNotIp(self):
    self.assertFalse(dnsregex.isIpAddress("..."))


  # Note: we currently do not check that the values are actually
  # in [0, 255] per segment, nor do we rule out multi-cast addresses,
  # etc. We really just care about getting between 1 and 4 numbers


  ### tests for DNS address resolution ###

  def testEmptyDnsNoMatch(self):
    self.assertFalse(dnsregex.isDnsName(""))

  def testWordMatch(self):
    self.assertTrue(dnsregex.isDnsName("foo"))

  def testWordDotNoMatch(self):
    self.assertFalse(dnsregex.isDnsName("foo."))

  def testWordChainMatch(self):
    self.assertTrue(dnsregex.isDnsName("foo.bar.baz"))

  def testWordChainDotNoMatch(self):
    self.assertFalse(dnsregex.isDnsName("foo.bar.baz."))

  def testNumLeaderNoMatch(self):
    self.assertFalse(dnsregex.isDnsName("1foo.bar.baz"))

  def testNumLeaderNoMatch2(self):
    self.assertFalse(dnsregex.isDnsName("foo.1bar.baz"))

  def testNumMiddleOk(self):
    self.assertTrue(dnsregex.isDnsName("test1foo.bar.baz"))

  def testUnderLeadFail(self):
    self.assertFalse(dnsregex.isDnsName("_foo.bar.baz"))

  def testUnderMidOk(self):
    self.assertTrue(dnsregex.isDnsName("some_foo.bar.baz"))

  def testDashLeadFail(self):
    self.assertFalse(dnsregex.isDnsName("-foo.bar.baz"))

  def testDashMidOk(self):
    self.assertTrue(dnsregex.isDnsName("some-foo.bar.baz"))

  def testMultiDotsNotDns(self):
    self.assertFalse(dnsregex.isDnsName("foo..bar"))

  def testJustDotsNotDns(self):
    self.assertFalse(dnsregex.isDnsName("."))

  def testJustDotsNotDns2(self):
    self.assertFalse(dnsregex.isDnsName(".."))

  def testJustDotsNotDns3(self):
    self.assertFalse(dnsregex.isDnsName("..."))

  def testTheLuggageCode(self):
    self.assertTrue(dnsregex.isDnsName("one.two.three.four.five"))


if __name__ == '__main__':
  unittest.main()

