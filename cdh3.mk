CDH_VERSION=3

# Hadoop 0.20.0-based hadoop package
HADOOP20_NAME=hadoop
HADOOP20_PKG_NAME=hadoop-0.20
HADOOP20_BASE_VERSION=0.20.2
HADOOP20_SOURCE=hadoop-$(HADOOP20_BASE_VERSION)-r916569.tar.gz
HADOOP20_SOURCE_MD5=1d6bd7ef87ae9a8f603d195a6f9fcfbd
HADOOP20_SITE=$(CLOUDERA_ARCHIVE)
HADOOP20_GIT_REPO=$(BASE_DIR)/repos/cdh3/hadoop-0.20
HADOOP20_BASE_REF=cdh-base-$(HADOOP20_BASE_VERSION)
HADOOP20_BUILD_REF=HEAD
HADOOP20_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/hadoop-0.20-package
$(eval $(call PACKAGE,hadoop20,HADOOP20))

# Pig 
PIG_BASE_VERSION=0.7.0
PIG_NAME=pig
PIG_PKG_NAME=hadoop-pig
PIG_SOURCE=pig-0.7.0.tar.gz
PIG_GIT_REPO=$(BASE_DIR)/repos/cdh3/pig
PIG_BASE_REF=cdh-base-$(PIG_BASE_VERSION)
PIG_BUILD_REF=cdh-$(PIG_BASE_VERSION)
PIG_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/pig-package
PIG_SITE=$(APACHE_MIRROR)/hadoop/pig/pig-$(PIG_BASE_VERSION)
$(eval $(call PACKAGE,pig,PIG))

# Oozie
OOZIE_NAME=oozie
OOZIE_PKG_NAME=oozie
OOZIE_BASE_VERSION=1.6.2
OOZIE_SOURCE=cdh-base-$(OOZIE_BASE_VERSION).tar.gz 
OOZIE_GIT_REPO=$(BASE_DIR)/repos/cdh3/oozie
OOZIE_BASE_REF=cdh-base-$(OOZIE_BASE_VERSION)
OOZIE_BUILD_REF=cdh-$(OOZIE_BASE_VERSION)
OOZIE_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/oozie-package
OOZIE_SITE=http://git.sf.cloudera.com/index.cgi/oozie.git/snapshot
$(eval $(call PACKAGE,oozie,OOZIE))

# Oozie-client
OOZIE-CLIENT_NAME=oozie-client
OOZIE-CLIENT_PKG_NAME=oozie-client
OOZIE-CLIENT_BASE_VERSION=1.6.2
OOZIE-CLIENT_SOURCE=cdh-base-$(OOZIE-CLIENT_BASE_VERSION).tar.gz 
OOZIE-CLIENT_GIT_REPO=$(BASE_DIR)/repos/cdh3/oozie
OOZIE-CLIENT_BASE_REF=cdh-base-$(OOZIE-CLIENT_BASE_VERSION)
OOZIE-CLIENT_BUILD_REF=cdh-$(OOZIE-CLIENT_BASE_VERSION)
OOZIE-CLIENT_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/oozie-client-package
OOZIE-CLIENT_SITE=http://git.sf.cloudera.com/index.cgi/oozie.git/snapshot
$(eval $(call PACKAGE,oozie-client,OOZIE-CLIENT))

# Hive
HIVE_NAME=hive
HIVE_RELEASE=2
HIVE_PKG_NAME=hadoop-hive
HIVE_BASE_VERSION=0.5.0
HIVE_SOURCE=hive-$(HIVE_BASE_VERSION)-dev.tar.gz
HIVE_GIT_REPO=$(BASE_DIR)/repos/cdh3/hive
HIVE_BASE_REF=cdh-base-$(HIVE_BASE_VERSION)
HIVE_BUILD_REF=cdh-$(HIVE_BASE_VERSION)
HIVE_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/hive-package
HIVE_SITE=$(APACHE_MIRROR)/hadoop/hive/hive-$(HIVE_BASE_VERSION)/
$(eval $(call PACKAGE,hive,HIVE))

HIVE_ORIG_SOURCE_DIR=$(HIVE_BUILD_DIR)/source
HIVE_SOURCE_DIR=$(HIVE_BUILD_DIR)/source/src

$(HIVE_TARGET_PREP):
	mkdir -p $($(PKG)_SOURCE_DIR)
	$(BASE_DIR)/tools/setup-package-build $($(PKG)_GIT_REPO) $($(PKG)_BASE_REF) $($(PKG)_BUILD_REF) $(DL_DIR)/$($(PKG)_SOURCE) $(HIVE_BUILD_DIR)/source
	rsync -av $(HIVE_ORIG_SOURCE_DIR)/cloudera/ $(HIVE_SOURCE_DIR)/cloudera/
	touch $@

# HBase
HBASE_NAME=hbase
HBASE_PKG_NAME=hadoop-hbase
HBASE_BASE_VERSION=0.89.20100621
HBASE_SOURCE=hbase-$(HBASE_BASE_VERSION)-src.tar.gz
HBASE_GIT_REPO=$(BASE_DIR)/repos/cdh3/hbase
HBASE_BASE_REF=cdh-base-$(HBASE_BASE_VERSION)
HBASE_BUILD_REF=cdh-$(HBASE_BASE_VERSION)
HBASE_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/hbase-package
HBASE_SITE=http://people.apache.org/~todd/hbase-0.89.20100621.rc0/
$(eval $(call PACKAGE,hbase,HBASE))

# ZooKeeper
ZOOKEEPER_NAME=zookeeper
ZOOKEEPER_PKG_NAME=hadoop-zookeeper
ZOOKEEPER_BASE_VERSION=3.3.1
ZOOKEEPER_SOURCE=zookeeper-$(ZOOKEEPER_BASE_VERSION).tar.gz
ZOOKEEPER_GIT_REPO=$(BASE_DIR)/repos/cdh3/zookeeper
ZOOKEEPER_BASE_REF=cdh-base-$(ZOOKEEPER_BASE_VERSION)
ZOOKEEPER_BUILD_REF=cdh-$(ZOOKEEPER_BASE_VERSION)
ZOOKEEPER_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/zookeeper-package
ZOOKEEPER_SITE=$(APACHE_MIRROR)/hadoop/zookeeper/zookeeper-$(ZOOKEEPER_BASE_VERSION)
$(eval $(call PACKAGE,zookeeper,ZOOKEEPER))

# Flume
FLUME_NAME=flume
FLUME_PKG_NAME=flume
FLUME_BASE_VERSION=0.9.0
FLUME_SOURCE=cdh-base-$(FLUME_BASE_VERSION).tar.gz
FLUME_GIT_REPO=$(BASE_DIR)/repos/cdh3/flume
FLUME_BASE_REF=cdh-base-$(FLUME_BASE_VERSION)
FLUME_BUILD_REF=cdh-$(FLUME_BASE_VERSION)
FLUME_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/flume-package
FLUME_SITE=http://git.sf.cloudera.com/index.cgi/flume.git/snapshot/
$(eval $(call PACKAGE,flume,FLUME))

# Sqoop
SQOOP_NAME=sqoop
SQOOP_PKG_NAME=sqoop
SQOOP_BASE_VERSION=1.1.0
SQOOP_SOURCE=cdh-base-$(SQOOP_BASE_VERSION).tar.gz
SQOOP_GIT_REPO=$(BASE_DIR)/repos/cdh3/sqoop
SQOOP_BASE_REF=cdh-base-$(SQOOP_BASE_VERSION)
SQOOP_BUILD_REF=cdh-$(SQOOP_BASE_VERSION)
SQOOP_PACKAGE_GIT_REPO=$(BASE_DIR)/repos/cdh3/sqoop-package
SQOOP_SITE=http://git.sf.cloudera.com/index.cgi/sqoop.git/snapshot/
$(eval $(call PACKAGE,sqoop,SQOOP))
sqoop-relnotes: sqoop
	cd $(SQOOP_GIT_REPO) && ant relnotes -Dversion=$(SQOOP_FULL_VERSION) \
		-Dbuild.relnotes.dir=$(SQOOP_OUTPUT_DIR)

