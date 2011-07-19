#
# Module for emitting Apache style release note HTML.
#

import cgi
from utils import getJiraIssueURL

def printHeader(cdhReleaseVersion, baseVersion, cdhProjectVersion, cdhProjectName):
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

The following lists all %(cdhProjectName)s Jiras included in %(cdhReleaseVersion)s
that are not included in the %(cdhProjectName)s base version %(baseVersion)s. The
<a href='%(cdhProjectVersion)s.CHANGES.txt'>%(cdhProjectVersion)s.CHANGES.txt</a>
file lists all changes included in %(cdhReleaseVersion)s. The patch for each
change can be found in the cloudera/patches directory in the release tarball.

<h2>Changes Not In %(cdhProjectName)s %(baseVersion)s </h2>""" % \
        {'cdhReleaseVersion' : cdhReleaseVersion,
         'baseVersion' : baseVersion,
         'cdhProjectVersion' : cdhProjectVersion,
         'cdhProjectName' : cdhProjectName }


def printFooter(jiraCount):

    if not jiraCount:
        print "No patches have been applied that are not in the upstream version."

    print "</body>"
    print "</html>"


def printProject(jiraDict, proj, projName):
    """Print the HTML for an individual project"""

    jiraCount = 0

    try:
        typeDict = jiraDict[proj]
        jiraTypes = typeDict.keys()
        jiraTypes.sort()

        print "<h3>"+projName+"</h3>"

        for jt in jiraTypes:
            print "<h4>"+jt+"</h4>"
            print "<ul>"
            for (jira, summary) in typeDict[jt]:
                url = getJiraIssueURL(jira)
                summary = cgi.escape(summary)
                print "<li>[<a href='"+url+"'>"+jira+"</a>] - "+summary+"</li>"
                jiraCount += 1
            print "</ul>"
    except KeyError:
        # No tickets of this key
        pass

    return jiraCount


def printRelNotes(cdhReleaseVersion, baseVersion, cdhProjectVersion,
                  cdhProjectName, jiraDict):
    """Print HTML for release notes. jiraDict should be of form:
       jiraDict[proj][jiraType] = list of (jira, summary) pairs, eg
       jiraDict["HDFS"]["Bug"] = [("HDFS-127","Fix a bug")]
    """
    jiraCount = 0

    printHeader(cdhReleaseVersion, baseVersion, cdhProjectVersion, cdhProjectName)
    jiraCount += printProject(jiraDict, "DISTRO", "CDH")
    jiraCount += printProject(jiraDict, "HADOOP", "Common")
    jiraCount += printProject(jiraDict, "HDFS", "HDFS")
    jiraCount += printProject(jiraDict, "MAPREDUCE", "MapReduce")
    jiraCount += printProject(jiraDict, "ZOOKEEPER", "Zookeeper")
    jiraCount += printProject(jiraDict, "PIG", "Pig")
    jiraCount += printProject(jiraDict, "HBASE", "HBase")
    jiraCount += printProject(jiraDict, "HIVE", "Hive")
    jiraCount += printProject(jiraDict, "OOZIE", "Oozie")
    jiraCount += printProject(jiraDict, "WHIRR", "Whirr")
    jiraCount += printProject(jiraDict, "SQOOP", "Sqoop")
    jiraCount += printProject(jiraDict, "FLUME", "Flume")
    
    printFooter(jiraCount)
