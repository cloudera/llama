
def getApacheJiraList():
    return ["HADOOP","HDFS","MAPREDUCE", "ZOOKEEPER", "PIG", "HIVE",
            "OOZIE", "HBASE", "WHIRR", "SQOOP", "FLUME", "BIGTOP",
            "HCATALOG", "HCAT"]

def getClouderaJiraList():
    return ["DISTRO","HUE"]

def getJiraList():
    return getApacheJiraList() + getClouderaJiraList()

def isClouderaJira(jira):
    jiraType = jira.split("-")[0]
    return jiraType in getClouderaJiraList()

def getJiraIssueXMLURL(jira):
    if isClouderaJira(jira):
        return "http://issues.%s.org/si/jira.issueviews:issue-xml/%s/%s.xml" \
            % ("cloudera", jira, jira)
    return "https://issues.%s.org/jira/si/jira.issueviews:issue-xml/%s/%s.xml" \
        % ("apache", jira, jira)

def getJiraIssueURL(jira):
    if isClouderaJira(jira):
        return "https://issues.%s.org/browse/%s" % ("cloudera", jira)
    return "https://issues.%s.org/jira/browse/%s" % ("apache", jira)
