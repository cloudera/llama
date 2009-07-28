DATE:=$(shell date +%Y%m%d)
BASE_DIR:=$(shell pwd)
BUILD_DIR:=$(BASE_DIR)/build
DL_DIR:=$(BASE_DIR)/dl
STAMP_DIR:=$(BUILD_DIR)/stamps

include config.mk

TARGETS:=

all: $(BUILD_DIR) $(DL_DIR) $(STAMP_DIR) packages

$(BUILD_DIR):
	mkdir -p $@
 
$(DL_DIR):
	mkdir -p $@

$(STAMP_DIR):
	mkdir -p $@

HADOOP18_SOURCE=hadoop-0.18.3.tar.gz
HADOOP18_SITE=http://apache.cloudera.com/hadoop/core/hadoop-0.18.3
HADOOP18_GIT_REPO=$(BASE_DIR)/repos/hadoop-0.18
HADOOP18_BASE_VERSION=0.18.3
HADOOP18_BASE_REF=release-0.18.3-with-jdiff
HADOOP18_BUILD_REF=cdh-0.18.3
HADOOP18_BUILD_DIR=$(BUILD_DIR)/hadoop18

$(DL_DIR)/$(HADOOP18_SOURCE):
	wget -P $(DL_DIR) --progress bar $(HADOOP18_SITE)/$(HADOOP18_SOURCE)
	touch $@

$(STAMP_DIR)/hadoop18prep:
	mkdir -p $(HADOOP18_BUILD_DIR)
	$(BASE_DIR)/tools/setup-package-build $(HADOOP18_GIT_REPO) $(HADOOP18_BASE_REF) $(HADOOP18_BUILD_REF) $(DL_DIR)/$(HADOOP18_SOURCE) $(HADOOP18_BUILD_DIR)
	touch $@

$(STAMP_DIR)/hadoop18patch:
	$(HADOOP18_BUILD_DIR)/cloudera/apply-patches $(HADOOP18_BUILD_DIR) $(HADOOP18_BUILD_DIR)/cloudera/patches
	touch $@

$(STAMP_DIR)/hadoop18build:
	/usr/bin/env JAVA32_HOME=$(JAVA32_HOME) JAVA64_HOME=$(JAVA64_HOME) JAVA5_HOME=$(JAVA5_HOME) FORREST_HOME=$(FORREST_HOME) $(HADOOP18_BUILD_DIR)/cloudera/do-release-build
	touch $@

hadoop18-download: $(DL_DIR)/$(HADOOP18_SOURCE) 

hadoop18-prep: hadoop18-download $(STAMP_DIR)/hadoop18prep

hadoop18-patch: hadoop18-prep $(STAMP_DIR)/hadoop18patch

hadoop18-clean: 
	-rm -f $(STAMP_DIR)/hadoop18*
	-rm -rf $(HADOOP18_BUILD_DIR)

hadoop18: hadoop18-patch $(STAMP_DIR)/hadoop18build
TARGETS += hadoop18

packages: $(TARGETS)

.PHONY: hadoop18 hadoop18-clean hadoop18-patch hadoop18-prep hadoop18-download
