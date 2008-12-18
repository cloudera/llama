# (c) Copyright 2008 Cloudera, Inc.
"""
This file has unit tests to
test parsing
"""

import unittest
import re

from log_to_dbs.scribe_hadoop import ScribeHadoopLogToDB

class TestParsers(unittest.TestCase):

  def testLogRegEx(self):
    # this is a log message generated without an AsyncAppender
    str = "server10.cloudera.com org.apache.hadoop.mapred.MapTask$MapOutputBuffer$Buffer 08/11/19 04:42:16 INFO etc"
    # this is a log message generated with an AsyncAppender
    str2 = "server10.cloudera.com ? 08/11/19 04:42:16 INFO etc"

    # these are both non-normal logs
    str3 = "\tat org.apache.testClass"
    str4 = "Caused by: java.io.EOFException"

    self.assertTrue(ScribeHadoopLogToDB.LOG_MATCH.search(str))
    self.assertTrue(ScribeHadoopLogToDB.LOG_MATCH.search(str2))
    self.assertFalse(ScribeHadoopLogToDB.LOG_MATCH.search(str3))
    self.assertFalse(ScribeHadoopLogToDB.LOG_MATCH.search(str4))
