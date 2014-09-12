
def getApacheJiraMap():
    return {"HADOOP" : "Common",
            "HDFS" : "HDFS",
            "MAPREDUCE" : "MapReduce",
            "YARN": "YARN",
            "ZOOKEEPER" : "ZooKeeper",
            "PIG" : "Pig",
            "HIVE" : "Hive",
            "OOZIE" : "Oozie",
            "HBASE" : "HBase",
            "WHIRR" : "Whirr",
            "SQOOP" : "Sqoop",
            "FLUME" : "Flume",
            "BIGTOP" : "Bigtop",
            "HCATALOG" : "HCatalog",
            "CRUNCH" : "Crunch",
            "SENTRY" : "Sentry",
            "SOLR" : "Solr",
            "LUCENE" : "Lucene",
            "AVRO" : "Avro",
            "MAHOUT" : "Mahout",
            "SPARK" : "Spark" }

def getApacheJiraList():
    return getApacheJiraMap().keys()

def getClouderaJiraMap():
    return {"DISTRO" : "CDH", "HUE" : "Hue", "IMPALA": "Impala"}

def getClouderaJiraList():
    return getClouderaJiraMap().keys()

def getJiraMap():
    return dict(getApacheJiraMap().items() + getApacheJiraMap().items())

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
