#
# Module for emitting Apache style release note HTML.
#

import cgi
from utils import getJiraIssueURL

def printHeader(cdhReleaseVersion, baseVersion, cdhHadoopVersion):
    print """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title> %(cdhReleaseVersion)s Release Notes</title>
<STYLE type="text/css">
H1 {font-family: sans-serif}
H2 {font-family: sans-serif; margin-left: 7mm}
TABLE {margin-left: 7mm}
</STYLE>
</head>
<body>
<h1>%(cdhReleaseVersion)s Release Notes</h1>

The following lists all Apache Hadoop Jiras included in %(cdhReleaseVersion)s
that are not included in the Apache Hadoop base version %(baseVersion)s. The 
<a href='%(cdhHadoopVersion)s.CHANGES.txt'>%(cdhHadoopVersion)s.CHANGES.txt</a>
file lists all changes included in %(cdhReleaseVersion)s. The patch for each 
change can be found in the cloudera/patches directory in the release tarball.

<h2>Changes Not In Hadoop %(baseVersion)s </h2>""" % \
        {'cdhReleaseVersion' : cdhReleaseVersion, 
         'baseVersion' : baseVersion, 
         'cdhHadoopVersion' : cdhHadoopVersion}


def printFooter():
    print "</body>"
    print "</html>"


def printProject(jiraDict, proj, projName):
    """Print the HTML for an individual project"""

    print "<h3>"+projName+"</h3>"
    typeDict = jiraDict[proj]
    jiraTypes = typeDict.keys()
    jiraTypes.sort()

    for jt in jiraTypes:
        print "<h4>"+jt+"</h4>"
        print "<ul>"
        for (jira, summary) in typeDict[jt]:
            url = getJiraIssueURL(jira)
            summary = cgi.escape(summary)
            print "<li>[<a href='"+url+"'>"+jira+"</a>] - "+summary+"</li>"
        print "</ul>"


def printRelNotes(cdhReleaseVersion, baseVersion, cdhHadoopVersion, jiraDict):
    """Print HTML for release notes. jiraDict should be of form:
       jiraDict[proj][jiraType] = list of (jira, summary) pairs, eg
       jiraDict["HDFS"]["Bug"] = [("HDFS-127","Fix a bug")]
    """
    printHeader(cdhReleaseVersion, baseVersion, cdhHadoopVersion)
    printProject(jiraDict, "DISTRO", "CDH")
    printProject(jiraDict, "HADOOP", "Common")
    printProject(jiraDict, "HDFS", "HDFS")
    printProject(jiraDict, "MAPREDUCE", "MapReduce")
    printFooter()
