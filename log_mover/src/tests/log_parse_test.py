# (c) Copyright 2009 Cloudera, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
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
