#!/usr/bin/env python2.5
# (c) Copyright 2009 Cloudera, Inc.

from __future__ import with_statement
from get_jira import fetch_description
from optparse import OptionParser
import urllib2
import re
import sys

ISSUE_TYPES = 'HADOOP|MAPREDUCE|HDFS|HIVE|PIG'
CDH_PATTERN = re.compile('(\\b(%s)-\\d+):' % ISSUE_TYPES)
YAHOO_PATTERN = re.compile('\\d+\\.\\s+(\\b(%s)-\\d+)' % ISSUE_TYPES)

CDH_ONLY = '+'
YAHOO_ONLY = '-'
BOTH_DISTROS = '='

def parse_changeset(pattern, filename):
  """ Look for a given pattern in a file. Each time the pattern is found add
  it to a set to be returned."""

  changes = set()

  if not filename.startswith('http'):
    filename = 'file://' + filename

  change_file = urllib2.urlopen(filename)
  for line in change_file:
    matches = pattern.search(line.strip())
    if matches:
      changes.add(matches.group(1))

  return changes

def parse_cdh_changeset(filename):
  """ CDH changesets are formatted in a typical changelog style.
  
  commit f0eddea626eacec5632438e729344b93d1d1d3fd
  Author: Aaron Kimball <aaron@cloudera.com>
  Date:   Thu Aug 13 16:20:41 2009 -0700
  
      Updated Sqoop documentation for MAPREDUCE-816, MAPREDUCE-789
  
  commit d52f1630acb6b2c56528448ccb464a3ca88f47ef
  Author: Aaron Kimball <aaron@cloudera.com>
  Date:   Thu Aug 13 15:44:36 2009 -0700
  
      MAPREDUCE-789: Oracle support for Sqoop
  
  commit 562940a86cfaee351e8cb23da5c3a855e1ff2f75
  Author: Aaron Kimball <aaron@cloudera.com>
  Date:   Thu Aug 13 15:40:16 2009 -0700
  
      MAPREDUCE-840: DBInputFormat leaves open transaction
  
  commit 0d5be1ec60ac19feea4948407b802d31d8a3bffe
  Author: Aaron Kimball <aaron@cloudera.com>
  Date:   Thu Aug 13 15:38:21 2009 -0700
  
      MAPREDUCE-816: Rename "local" mysql import to "direct"
  
  commit f140c84fff35987291de813e049576f1081bf3f9
  Author: Aaron Kimball <aaron@cloudera.com>
  Date:   Thu Aug 13 15:37:25 2009 -0700
  
      MAPREDUCE-716: org.apache.hadoop.mapred.lib.db.DBInputformat not working with oracle
      
      Applied MAPREDUCE-716.4.branch20.patch
  """
  return parse_changeset(CDH_PATTERN, filename)

def parse_yahoo_changeset(filename):
  """ Yahoo changesets are listed per patch.
  
  40. MAPREDUCE-817. Add a cache for retired jobs with minimal job
    info and provide a way to access history file url

  39. MAPREDUCE-814. Provide a way to configure completed job history
    files to be on HDFS.

  38. MAPREDUCE-838 Fixes a problem in the way commit of task outputs
    happens. The bug was that even if commit failed, the task would be
    declared as successful. Contributed by Amareshwari Sriramadasu.

  37. MAPREDUCE-809 Fix job-summary logs to correctly record final status of 
    FAILED and KILLED jobs.  
  """
  return parse_changeset(YAHOO_PATTERN, filename)

def print_changes(changes, prefix, verbose):
  """ Print the change with the given prefix (-, +, =). If verbose also print a
  description"""

  for change in sorted(changes):
    if verbose:
      print ''.ljust(80, '*')
      print "%s%s:" % (prefix, change)
      print ''.ljust(len(change) + 2, '*') # account for the prefix and the :
      print fetch_description(change)
    else:
      print "%s%s" % (prefix, change)

def compare_changesets(cdh_set, yahoo_set, verbose):
  """ Print the differences and intersection for both sets of changes."""

  # The following patches are preset in CDH but not in Yahoo's distribution

  print_changes(cdh_set.difference(yahoo_set), CDH_ONLY, verbose)

  # The following patches are preset in Yahoo's distribution but not in CDH

  print_changes(yahoo_set.difference(cdh_set), YAHOO_ONLY, verbose)

  # The following patches are preset in both Yahoo's and CDH

  print_changes(cdh_set.intersection(yahoo_set), BOTH_DISTROS, verbose)

def main():
  " Compare CDH and Yahoo Hadoop Change files."

  usage = "usage: %prog [options] arg"
  parser = OptionParser(usage)
  parser.add_option("-v", action = "store_true", dest = "verbose", default = False)
  parser.add_option("-c", "--cdh", dest = "cdh_file",
                    help = "cdh changes file")
  parser.add_option("-y", "--yahoo", dest = "yahoo_file",
                    help = "yahoo changes file")

  (options, _) = parser.parse_args()

  if not options.cdh_file or not options.yahoo_file:
    print "Please specify a CDH changes and Yahoo changes file."
    sys.exit(1)

  compare_changesets(parse_cdh_changeset(options.cdh_file),
                     parse_yahoo_changeset(options.yahoo_file),
                     options.verbose)

if __name__ == "__main__":
  main()

