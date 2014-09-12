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
from xml.parsers.expat import ExpatError
from relnotehtml import printRelNotes
from utils import getJiraIssueXMLURL, getJiraList
from pymongo import MongoClient

mongoHost = "m0709.mtv.cloudera.com"
mongoPort = 27017
mongoDb = "jiras"

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
  parser.add_argument('-c','--cdh_project_version', required=True)
  parser.add_argument('-n','--cdh_project_name', required=True)
  return parser.parse_args()


def addJira(proj, jiraType, jira, summary):
    """ Add given jira/summary pair to the dictionary """

    global jiraDict
    projDict = jiraDict.setdefault(proj, {})
    jiras = projDict.setdefault(jiraType, [])
    p = (jira, summary)
    if p not in jiras:
      jiras.append(p)


def getJiraDOM(jira):
  """ Get the DOM for the given JIRA. Retries if the request was
  ill-formatted (intermittent failure) and bails if there was
  an error parsing the response (invalid jira).
  """

  dom = None
  while dom is None:
    try:
      xml = urlopen(getJiraIssueXMLURL(jira)).read()
      if -1 != xml.find("<title>400 Bad Request</title>"):
        print >> sys.stderr, "ERROR. Retry %s" % (jira)
        continue
      dom = ElementTree.fromstring(xml)
    except ExpatError as e:
      print >> sys.stderr, "ERROR. Skip %s (%s)" % (jira, e)
      return None
    except ElementTree.ParseError as e:
      print >> sys.stderr, "ERROR. Not finding %s" % (jira)
      return None
  return dom


def parseJiras(commitLog, mongoJira):
    """ Parse jiras and add them to the dictionary """

    # A git object identifier is 40 chars
    jiras = "|".join(getJiraList())
    jiraReg = r"^\w{40} .*("+jiras+")[- ](\d+)"
    for m in re.finditer(jiraReg, commitLog, re.M):
        proj = m.group(1)
        num  = m.group(2)
        jira = proj+"-"+num
        summary = ""
        jiraType = ""

        cachedJira = mongoJira.find_one({"jira": jira})
        if cachedJira is None:
          cachedJira = {}
          dom = getJiraDOM(jira)
          if dom is None:
            continue
          cachedJira['jira'] = jira
          cachedJira['proj'] = proj
          cachedJira['summary'] = dom.find('./channel/item/summary').text
          cachedJira['jiraType'] = dom.find('./channel/item/type').text
          # For Sub-tasks use the type of the parent jira
          if cachedJira['jiraType'] == "Sub-task":
            p = getJiraDOM(dom.find('./channel/item/parent').text)
            cachedJira['jiraType'] = p.find('./channel/item/type').text
            # Progress tick
          mongoJira.insert(cachedJira)
        
        print >> sys.stderr, ".",
        addJira(proj, cachedJira['jiraType'], jira, cachedJira['summary'])


def main():
    args = parse_args()
    commitLog = open(args.commit_log, 'r').read()
    mongoJira = MongoClient(mongoHost, mongoPort)[mongoDb].jiras
    
    parseJiras(commitLog, mongoJira)
    printRelNotes(args.cdh_release_version,
                  args.apache_base_version,
                  args.cdh_project_version,
                  args.cdh_project_name,
                  jiraDict)


if __name__ == "__main__":
  sys.exit(main())
