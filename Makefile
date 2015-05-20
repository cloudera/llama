BASE_DIR  :=$(shell pwd)
BUILD_DIR ?=$(BASE_DIR)/build
DL_DIR    ?=$(BASE_DIR)/dl
OUTPUT_DIR?=$(BASE_DIR)/output
REPO_DIR  ?=$(BASE_DIR)/repos
CONFIG    ?=$(BASE_DIR)/config.mk

MAJOR_VERSION ?= 5
LONG_VERSION ?= 5.4.3

CDH       ?=cdh$(MAJOR_VERSION)
GPLEXTRAS ?=gplextras$(MAJOR_VERSION)
CDH_VERSION_STRING ?= cdh$(LONG_VERSION)-SNAPSHOT
CDH_REL_STRING ?= cdh$(LONG_VERSION)
GPLEXTRAS_REL_STRING ?= gplextras$(LONG_VERSION)

# Only defined if we are doing a beta release. This will
# show up in the release identifier
#BETA_VERSION=5b2
#CDH_BETA_REL_STRING ?= cdh$(BETA_VERSION)
#GPLEXTRAS_BETA_REL_STRING ?= gplextras$(BETA_VERSION)

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
ifndef JAVA_HOME
$(error Please set JAVA_HOME in $(CONFIG) or environment)
endif
#ifndef JAVA7_HOME
#$(error Please set JAVA_HOME in $(CONFIG) or environment)
#endif
ifndef JAVA5_HOME
$(error Please set JAVA5_HOME in $(CONFIG) or environment)
endif
ifndef FORREST_HOME
$(error Please set FORREST_HOME in $(CONFIG) or environment)
endif

#JAVA_HOME := $(JAVA7_HOME)
#JAVA64_HOME := $(JAVA7_HOME)

# Default Apache mirror
APACHE_MIRROR ?= http://mirrors.ibiblio.org/apache
CLOUDERA_ARCHIVE ?= http://archive.cloudera.com/tarballs/

# Include the implicit rules and functions for building packages
include package.mk

include $(CDH_MAKEFILE)

help: package-help

all: srpm sdeb tgz
world: all

packages: install-pom $(TARGETS)
tgz: packages

help-header:
	@echo "    $(CDH) targets:"
	@echo "    all       (all TGZs/SRPMS/SDEBS)"
	@echo "    tgz       (all Source TGZs)"
	@echo "    srpm      (all SRPMs)"
	@echo "    rpm       (all RPMs)"
	@echo "    sdeb      (all SDEBs)"
	@echo "    deb       (all DEBs)"
	@echo "    relnotes  (all relnotes)"
	@echo "    clean     (remove build dir)"
	@echo "    realclean (also remove output and download dirs)"
	@echo "    distclean (also remove the repo dir)"

package-help: help-header $(TARGETS_HELP)

clean: $(TARGETS_CLEAN)
	-rm -rf $(BUILD_DIR)

realclean: clean
	-rm -rf $(OUTPUT_DIR)
	-rm -rf $(DL_DIR)

distclean: realclean
	-rm -rf $(REPO_DIR)

srpm: install-pom $(TARGETS_SRPM)

rpm: install-pom $(TARGETS_RPM)

sdeb: install-pom $(TARGETS_SDEB)

deb: install-pom $(TARGETS_DEB)

relnotes: $(TARGETS_RELNOTES)

install-pom:
	[ -f "$(BASE_DIR)/pom.xml" ] && mvn -N install $(DO_MAVEN_DEPLOY)

components:
	@echo $(shell echo $(TARGETS) | sed -e 's/ [^ ]*-lzo//g' | sed -e 's/ gplextras-parcel//' | sed -e 's/ /,/g')

gpl-components:
	@echo "hadoop-lzo,impala-lzo"

src-parcel:
	rm -rf $(CDH_PARCEL_OUTPUT_DIR)
	mkdir -p $(CDH_PARCEL_OUTPUT_DIR)
	@echo "PKG_CDH_VERSION=\"$(CDH_PARCEL_BASE_VERSION)\"" > $(CDH_PARCEL_OUTPUT_DIR)/cdh-parcel.props
	@echo "CDH_PARCEL_BASE_VERSION=\"$(CDH_REL_STRING)\"" >> $(CDH_PARCEL_OUTPUT_DIR)/cdh-parcel.props
	@echo "CDH_CUSTOMER_PATCH=\"$(CDH_CUSTOMER_PATCH)\"" >> $(CDH_PARCEL_OUTPUT_DIR)/cdh-parcel.props
	@echo "CDH_PARCEL_VERSION=\"$(REL_STRING)\"" >> $(CDH_PARCEL_OUTPUT_DIR)/cdh-parcel.props
	@echo "CDH_PARCEL_RELEASE=\"$(CDH_PARCEL_RELEASE)\"" >> $(CDH_PARCEL_OUTPUT_DIR)/cdh-parcel.props

gpl-src-parcel:
	rm -rf $(GPLEXTRAS_PARCEL_OUTPUT_DIR)
	mkdir -p $(GPLEXTRAS_PARCEL_OUTPUT_DIR)
	@echo "PKG_GPLEXTRAS_VERSION=\"$(GPLEXTRAS_PARCEL_BASE_VERSION)\"" > $(GPLEXTRAS_PARCEL_OUTPUT_DIR)/gplextras-parcel.props
	@echo "GPLEXTRAS_PARCEL_BASE_VERSION=\"$(GPLEXTRAS_REL_STRING)\"" >> $(GPLEXTRAS_PARCEL_OUTPUT_DIR)/gplextras-parcel.props
	@echo "CDH_CUSTOMER_PATCH=\"$(CDH_CUSTOMER_PATCH)\"" >> $(GPLEXTRAS_PARCEL_OUTPUT_DIR)/gplextras-parcel.props
	@echo "GPLEXTRAS_PARCEL_VERSION=\"$(REL_STRING)\"" >> $(GPLEXTRAS_PARCEL_OUTPUT_DIR)/gplextras-parcel.props
	@echo "GPLEXTRAS_PARCEL_RELEASE=\"$(GPLEXTRAS_PARCEL_RELEASE)\"" >> $(GPLEXTRAS_PARCEL_OUTPUT_DIR)/gplextras-parcel.props

.DEFAULT_GOAL:= help
.PHONY: realclean clean distclean package-help help-header packages all world help tgz srpm sdeb install-pom relnotes components src-parcel gpl-components gpl-src-parcel


