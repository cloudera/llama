DATE:=$(shell date +%Y%m%d)
BASE_DIR:=$(shell pwd)
BUILD_DIR:=$(BASE_DIR)/build
DL_DIR:=$(BASE_DIR)/dl
STAMP_DIR:=$(BUILD_DIR)/stamps

JAVA32_HOME=/home/matt/bin/jdk1.6.0_14_i586
JAVA64_HOME=/home/matt/bin/jdk1.6.0_14_x86_64
JAVA5_HOME=/home/matt/bin/jdk1.5.0_19
FORREST_HOME=/home/matt/bin/apache-forrest-0.8

all: $(BUILD_DIR) $(DL_DIR) $(STAMP_DIR) hadoop18

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

$(STAMP_DIR)/hadoop18downloaded:
	wget -P $(DL_DIR) --progress bar $(HADOOP18_SITE)/$(HADOOP18_SOURCE)
	touch $@

$(STAMP_DIR)/hadoop18prepped:
	mkdir -p $(HADOOP18_BUILD_DIR)
	$(BASE_DIR)/tools/setup-package-build $(HADOOP18_GIT_REPO) $(HADOOP18_BASE_REF) $(HADOOP18_BUILD_REF) $(DL_DIR)/$(HADOOP18_SOURCE) $(HADOOP18_BUILD_DIR)
	touch $@

$(STAMP_DIR)/hadoop18patched:
	$(HADOOP18_BUILD_DIR)/cloudera/apply-patches $(HADOOP18_BUILD_DIR) $(HADOOP18_BUILD_DIR)/cloudera/patches
	touch $@

$(STAMP_DIR)/hadoop18build:
	/usr/bin/env JAVA32_HOME=$(JAVA32_HOME) JAVA64_HOME=$(JAVA64_HOME) JAVA5_HOME=$(JAVA5_HOME) FORREST_HOME=$(FORREST_HOME) $(HADOOP18_BUILD_DIR)/cloudera/do-release-build
	touch $@

hadoop18-prep: $(STAMP_DIR)/hadoop18prepped

hadoop18-download: $(STAMP_DIR)/hadoop18downloaded

hadoop18-patch: $(STAMP_DIR)/hadoop18patched

hadoop18-build: $(STAMP_DIR)/hadoop18build

hadoop18: hadoop18-download hadoop18-prep hadoop18-patch hadoop18-build

.PHONY: hadoop18-prep hadoop18-download hadoop18
