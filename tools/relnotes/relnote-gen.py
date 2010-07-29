#!/usr/bin/env python
#
# Run with '-h' for usage.
#
# Generates Apache style release note html given a file that
# contains a git log generated using the --pretty=oneline 
# and --abbrev-commit options.
#

import argparse
import re
import sys

from urllib import urlopen
from xml.etree import ElementTree
from relnotehtml import printRelNotes

#
#  Maps the project type and jira type to a list of pairs where
#  each pair describes a jira, eg
#    jiraDict["HDFS"]["Bug"]  =>  [ ("HDFS-xyz","Summary"), (..) ]
#
jiraDict = {}


def parse_args():
  """ Parse and return command line args """

  parser = argparse.ArgumentParser()
  parser.add_argument('-l','--commit_log', required=True)
  parser.add_argument('-a','--apache_base_version',required=True)
  parser.add_argument('-r','--cdh_release_version',required=True)
  parser.add_argument('-c','--cdh_hadoop_version', required=True)
  return parser.parse_args()


def addJira(proj, jiraType, jira, summary):
    """ Add given jira/summary pair to the dictionary """

    global jiraDict
    projDict = jiraDict.setdefault(proj, {})
    jiras = projDict.setdefault(jiraType, [])
    jiras.append((jira, summary))


def getJiraDOM(jira):
  xml = urlopen("http://issues.apache.org/jira/si/jira.issueviews:"+\
                "issue-xml/%s/%s.xml" % (jira, jira))
  return ElementTree.parse(xml)


def parseJiras(commitLog):
    """ Parse jiras and add them to the dictionary """

    # A git object identifier is 40 chars
    jiraReg = r"^\w{40} (HADOOP|HDFS|MAPREDUCE)[- ](\d+)"
    for m in re.finditer(jiraReg, commitLog, re.M):
        proj = m.group(1)
        num  = m.group(2)
        jira = proj+"-"+num
        doc = getJiraDOM(jira)
        summary = doc.find('./channel/item/summary').text
        jiraType = doc.find('./channel/item/type').text
        # For Sub-tasks use the type of the parent jira
        if jiraType == "Sub-task":
          p = getJiraDOM(doc.find('./channel/item/parent').text)
          jiraType = p.find('./channel/item/type').text
        addJira(proj, jiraType, jira, summary)


def main():
    args = parse_args()
    commitLog = open(args.commit_log, 'r').read()
    parseJiras(commitLog)
    printRelNotes(args.cdh_release_version,
                  args.apache_base_version,
                  args.cdh_hadoop_version,
                  jiraDict)


if __name__ == "__main__":
  sys.exit(main())
