# (c) Copyright 2008 Cloudera, Inc.
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

import sys
import unittest
import optparse

suites = {'parsers':
            {'module': 'tests',
             'submodule': 'log_parse_test',
             'class': 'TestParsers',
             'desc': "Tests parsers that parse Scribe logs"},
          }

def print_err_list(list):
  """
  Holder function in case we want to print errors nicely.
  """
  if len(list):
    for err in list:
      print "\t", err

def run_tests_in_suite(suite_name):
  suite_info = suites[suite_name]
  if suite_info.has_key("submodule"):
    module_name = suite_info['module'] + "." + suite_info['submodule']
    suite_module = __import__(module_name,
                              fromlist = [suite_info['submodule']])
  else:
    suite_module = __import__(suite_info['module'])
  suite_class = getattr(suite_module, suite_info['class'])
  test_suite = unittest.TestLoader().loadTestsFromTestCase(suite_class)
  print "Running %s tests" % suite_name

  result = unittest.TestResult()
  test_suite.run(result)
  if result.wasSuccessful():
    print "\tPassed %d test(s)!" % result.testsRun
  else:
    print "\tFailed!"
    print_err_list(result.errors)
    print_err_list(result.failures)

def run_tests_in_suites(suite_names):
  # TODO: make sure the suite name is in the "suites" dictionary
  for suite_name in suite_names:
    run_tests_in_suite(suite_name)  

if __name__ == "__main__":
  (script_name, script_args) = sys.argv[0], sys.argv[1:]

  parser = optparse.OptionParser()
  parser.add_option("-l", "--list-suites",
                    action = "store_true",
                    help = "Print available test suites and exit")
  (options, args) = parser.parse_args()

  # just print available test suites and exit
  if options.list_suites:
    print "Available test suites:"
    for suite, suite_info in suites.iteritems():
      print "\t" + suite + ": " + suite_info['desc']
    sys.exit(0)

  if len(args) == 0:
    # run all available test suites
    run_tests_in_suites(suites.keys())
  else:
    run_tests_in_suites(args)
