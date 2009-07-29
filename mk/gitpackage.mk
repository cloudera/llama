# Implicit targets

# Download 
# TODO: md5 check
$(STAMP_DIR)/%-download:
	wget -P $(DL_DIR) --progress bar $($(PKG)_SITE)/$($(PKG)_SOURCE)
	touch $@

# Prep
$(STAMP_DIR)/%-prep:
	mkdir -p $($(PKG)_BUILD_DIR)
	$(BASE_DIR)/tools/setup-package-build $($(PKG)_GIT_REPO) $($(PKG)_BASE_REF) $($(PKG)_BUILD_REF) $(DL_DIR)/$($(PKG)_SOURCE) $($(PKG)_BUILD_DIR)
	touch $@

# Patch
$(STAMP_DIR)/%-patch:
	$($(PKG)_BUILD_DIR)/cloudera/apply-patches $($(PKG)_BUILD_DIR) $($(PKG)_BUILD_DIR)/cloudera/patches
	touch $@

# Build
$(STAMP_DIR)/%-build:
	/usr/bin/env JAVA32_HOME=$(JAVA32_HOME) JAVA64_HOME=$(JAVA64_HOME) JAVA5_HOME=$(JAVA5_HOME) FORREST_HOME=$(FORREST_HOME) $($(PKG)_BUILD_DIR)/cloudera/do-release-build
	touch $@

# Git Package make function
# $1 is the target prefix, $2 is the variable prefix
define GITPACKAGE

$(2)_BUILD_DIR =   $(BUILD_DIR)/$(1)-build

$(2)_TARGET_DL    = $(STAMP_DIR)/$(1)-download
$(2)_TARGET_PREP  = $(STAMP_DIR)/$(1)-prep
$(2)_TARGET_PATCH = $(STAMP_DIR)/$(1)-patch
$(2)_TARGET_BUILD = $(STAMP_DIR)/$(1)-build

$(2)_FULL_VERSION:= $(shell cd $($(2)_GIT_REPO) && $(BASE_DIR)/tools/branch-tool version)
$(2)_OUTPUT = $(OUTPUT_DIR)/hadoop-$$($(2)_FULL_VERSION).tar.gz

# Step 5
# TODO: don't make this hadoop specific
#$$($(2)_TARGET_SAVE):
#	cp -f $$($(2)_BUILD_DIR)/build/hadoop-$$($(2)_FULL_VERSION).tar.gz $(OUTPUT_DIR)
#	touch $$@

# We download target when the source is not in the download directory
$(1)-download: $$($(2)_TARGET_DL) 

# To prep target, we need to download it first
$(1)-prep: $(1)-download $$($(2)_TARGET_PREP)

# To patch target, we need to prep it first
$(1)-patch: $(1)-prep $$($(2)_TARGET_PATCH)
 
# To build target, we need to patch it first
$(1): $(1)-patch $$($(2)_TARGET_BUILD)

# Implicit rules with PKG variable
$$($(2)_TARGET_DL):      PKG=$(2)
$$($(2)_TARGET_PREP):    PKG=$(2)
$$($(2)_TARGET_PATCH):   PKG=$(2)
$$($(2)_TARGET_BUILD):   PKG=$(2)

# Helper targets -version -help etc
$(1)-version:
	@echo "Base: $$($(2)_BASE_VERSION)"
	@echo "Full: $$($(2)_FULL_VERSION)"

$(1)-help:
	@echo "    $(1)"

$(1)-clean: 
	-rm -f $(STAMP_DIR)/$(1)*
	-rm -rf $$($(2)_BUILD_DIR)

TARGETS += $(1) 
TARGETS_HELP += $(1)-help
endef
