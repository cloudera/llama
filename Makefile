BASE_DIR  :=$(shell pwd)
BUILD_DIR ?=$(BASE_DIR)/build
DL_DIR    ?=$(BASE_DIR)/dl
OUTPUT_DIR?=$(BASE_DIR)/output
CONFIG    ?=$(BASE_DIR)/config.mk
CDH       ?=cdh2
CDH_MAKEFILE:=$(CDH).mk

REQUIRED_DIRS = $(BUILD_DIR) $(DL_DIR) $(OUTPUT_DIR)
_MKDIRS :=$(shell for d in $(REQUIRED_DIRS); \
  do                               \
    [ -d $$d ] || mkdir -p $$d;  \
  done) 

TARGETS:=
TARGETS_HELP:=
TARGETS_CLEAN:=

# Pull in the config variables
-include $(CONFIG)
ifndef JAVA32_HOME
$(error Please set JAVA32_HOME in $(CONFIG) or environment)
endif
ifndef JAVA64_HOME
$(error Please set JAVA64_HOME in $(CONFIG) or environment)
endif
ifndef JAVA5_HOME
$(error Please set JAVA5_HOME in $(CONFIG) or environment)
endif
ifndef FORREST_HOME
$(error Please set FORREST_HOME in $(CONFIG) or environment)
endif

# Default Apache mirror
APACHE_MIRROR ?= http://mirror.cloudera.com/apache/
CLOUDERA_ARCHIVE ?= http://archive.cloudera.com/tarballs/

# Include the implicit rules and functions for building packages
include package.mk

include $(CDH_MAKEFILE)

help: package-help

all: srpm sdeb tgz
world: all

packages: $(TARGETS)
tgz: packages

help-header:
	@echo "    $(CDH) targets:"
	@echo "    all     (all TGZs/SRPMS/SDEBS)"
	@echo "    tgz     (all Source TGZs)"
	@echo "    srpm    (all SRPMs)"
	@echo "    sdeb    (all SDEBs)"

package-help: help-header $(TARGETS_HELP)

clean: $(TARGETS_CLEAN)
	-rm -rf $(BUILD_DIR)

realclean: clean
	-rm -rf $(OUTPUT_DIR)
	-rm -rf $(DL_DIR)

srpm: $(TARGETS_SRPM)

sdeb: $(TARGETS_SDEB)

.DEFAULT_GOAL:= help
.PHONY: realclean clean package-help help-header packages all world help tgz srpm sdeb
