# Implicit targets

# Download 
$(BUILD_DIR)/%/.download:
	mkdir -p $(@D)
	[ -f $(DL_DIR)/$($(PKG)_SOURCE) ] || wget -P $(DL_DIR) --progress bar $($(PKG)_SITE)/$($(PKG)_SOURCE)
	touch $@

# Prep
$(BUILD_DIR)/%/.prep:
	mkdir -p $($(PKG)_BUILD_DIR)
	$(BASE_DIR)/tools/setup-package-build $($(PKG)_GIT_REPO) $($(PKG)_BASE_REF) $($(PKG)_BUILD_REF) $(DL_DIR)/$($(PKG)_SOURCE) $($(PKG)_BUILD_DIR)
	touch $@

# Patch
$(BUILD_DIR)/%/.patch:
	$($(PKG)_BUILD_DIR)/cloudera/apply-patches $($(PKG)_BUILD_DIR) $($(PKG)_BUILD_DIR)/cloudera/patches
	touch $@

# Build
$(BUILD_DIR)/%/.build:
	/usr/bin/env JAVA32_HOME=$(JAVA32_HOME) JAVA64_HOME=$(JAVA64_HOME) JAVA5_HOME=$(JAVA5_HOME) FORREST_HOME=$(FORREST_HOME) $($(PKG)_BUILD_DIR)/cloudera/do-release-build
	touch $@

# Git Package make function
# $1 is the target prefix, $2 is the variable prefix
define GITPACKAGE

# Calculate the full version based on the git patches
$(2)_FULL_VERSION:= $(shell cd $($(2)_GIT_REPO) && $(BASE_DIR)/tools/branch-tool version)

$(2)_BUILD_ROOT = $(BUILD_DIR)/$(1)/$$($(2)_FULL_VERSION)/
$(2)_BUILD_DIR       = $$($(2)_BUILD_ROOT)/build

# Define the file stamps
$(2)_TARGET_DL       = $$($(2)_BUILD_ROOT)/.download
$(2)_TARGET_PREP     = $$($(2)_BUILD_ROOT)/.prep
$(2)_TARGET_PATCH    = $$($(2)_BUILD_ROOT)/.patch
$(2)_TARGET_BUILD    = $$($(2)_BUILD_ROOT)/.build
$(2)_HOOK_POST_BUILD = $$($(2)_BUILD_ROOT)/.hook

# We download target when the source is not in the download directory
$(1)-download: $$($(2)_TARGET_DL) 

# To prep target, we need to download it first
$(1)-prep: $(1)-download $$($(2)_TARGET_PREP)

# To patch target, we need to prep it first
$(1)-patch: $(1)-prep $$($(2)_TARGET_PATCH)
 
# To build target, we need to patch it first
$(1): $(1)-patch $$($(2)_TARGET_BUILD) $$($(2)_HOOK_POST_BUILD)

#### 
# Helper targets -version -help etc
$(1)-version:
	@echo "Base: $$($(2)_BASE_VERSION)"
	@echo "Full: $$($(2)_FULL_VERSION)"

$(1)-help:
	@echo "    $(1)"

$(1)-clean: 
	-rm -rf $$($(2)_BUILD_ROOT)

# Implicit rules with PKG variable
$$($(2)_TARGET_DL):       PKG=$(2)
$$($(2)_TARGET_PREP):     PKG=$(2)
$$($(2)_TARGET_PATCH):    PKG=$(2)
$$($(2)_TARGET_BUILD):    PKG=$(2)
$$($(2)_HOOK_POST_BUILD): PKG=$(2)
$$($(2)_HOOK_POST_BUILD): PKG_FULL_VERSION=$$($(2)_FULL_VERSION)
$$($(2)_HOOK_POST_BUILD): PKG_BUILD_DIR=$$($(2)_BUILD_DIR)

# Default hook does nothing
$$($(2)_HOOK_POST_BUILD):

TARGETS += $(1) 
TARGETS_HELP += $(1)-help
endef
