BASE_DIR:=$(shell pwd)
BUILD_DIR:=$(BASE_DIR)/build
DL_DIR   ?=$(BASE_DIR)/dl
STAMP_DIR:=$(BUILD_DIR)/stamps

-include config.mk
include mk/gitpackage.mk

TARGETS:=

all: build-dirs packages

build-dirs:
	-@mkdir -p $(BUILD_DIR) $(DL_DIR) $(STAMP_DIR)

# Hadoop 0.18.3-based hadoop package
HADOOP18_SOURCE=hadoop-0.18.3.tar.gz
HADOOP18_SITE=http://apache.cloudera.com/hadoop/core/hadoop-0.18.3
HADOOP18_GIT_REPO=$(BASE_DIR)/repos/hadoop-0.18
HADOOP18_BASE_VERSION=0.18.3
HADOOP18_BASE_REF=release-0.18.3-with-jdiff
HADOOP18_BUILD_REF=cdh-0.18.3
$(eval $(call gitpackage,hadoop18,HADOOP18))

# Hadoop 0.20.0-based hadoop package
HADOOP20_SOURCE=hadoop-0.20.0.tar.gz
HADOOP20_SITE=http://apache.cloudera.com/hadoop/core/hadoop-0.20.0
HADOOP20_GIT_REPO=$(BASE_DIR)/repos/hadoop-0.20
HADOOP20_BASE_VERSION=0.20.0
HADOOP20_BASE_REF=apache/tags/release-0.20.0
HADOOP20_BUILD_REF=cdh-0.20.0
$(eval $(call gitpackage,hadoop20,HADOOP20))


packages: $(TARGETS)
