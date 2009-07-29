BASE_DIR  :=$(shell pwd)
BUILD_DIR ?=$(BASE_DIR)/build
DL_DIR    ?=$(BASE_DIR)/dl
STAMP_DIR :=$(BUILD_DIR)/stamps
OUTPUT_DIR?=$(BUILD_DIR)/output

REQUIRED_DIRS = $(BUILD_DIR) $(DL_DIR) $(STAMP_DIR) $(OUTPUT_DIR)
$(shell for d in $(REQUIRED_DIRS); \
  do                               \
    [ -d $$d ] || mkdir -p $$d;  \
  done) 

TARGETS:=
TARGETS_HELP:=

# optional configuration variables
-include config.mk

include mk/gitpackage.mk

help: package-help

all: packages
world: all

# Hadoop 0.18.3-based hadoop package
HADOOP18_BASE_VERSION=0.18.3
HADOOP18_SOURCE=hadoop-$(HADOOP18_BASE_VERSION).tar.gz
HADOOP18_SITE=http://apache.cloudera.com/hadoop/core/hadoop-$(HADOOP18_BASE_VERSION)
HADOOP18_GIT_REPO=$(BASE_DIR)/repos/hadoop-0.18
# jdiff workaround... bother.
HADOOP18_BASE_REF=release-0.18.3-with-jdiff
HADOOP18_BUILD_REF=cdh-$(HADOOP18_BASE_VERSION)
$(eval $(call GITPACKAGE,hadoop18,HADOOP18))

# Hadoop 0.20.0-based hadoop package
HADOOP20_BASE_VERSION=0.20.0
HADOOP20_SOURCE=hadoop-$(HADOOP20_BASE_VERSION).tar.gz
HADOOP20_SITE=http://apache.cloudera.com/hadoop/core/hadoop-$(HADOOP20_BASE_VERSION)
HADOOP20_GIT_REPO=$(BASE_DIR)/repos/hadoop-0.20
HADOOP20_BASE_REF=apache/tags/release-$(HADOOP20_BASE_VERSION)
HADOOP20_BUILD_REF=cdh-$(HADOOP20_BASE_VERSION)
$(eval $(call GITPACKAGE,hadoop20,HADOOP20))

# Pig 0.3.0
#PIG_SOURCE=pig-0.3.0.tar.gz
#PIG_SITE=http://apache.cloudera.com/hadoop/pig/pig-0.3.0
#PIG_GIT_REPO=$(BASE_DIR)/repos/pig
#PIG_BASE_VERSION=0.3.0
#PIG_BASE_REF=apache/tags/release-0.3.0
#PIG_BUILD_REF=cdh-0.18
#$(eval $(call gitpackage,pig,PIG))

packages: $(TARGETS) 

help-header:
	@echo "CDH targets:"
	@echo "    all (or world)"

package-help: help-header $(TARGETS_HELP)
