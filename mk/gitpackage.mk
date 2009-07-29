# Git Package make function
# $1 is the target prefix, $2 is the variable prefix
define GITPACKAGE

$(2)_BUILD_DIR =   $(BUILD_DIR)/$(1)-build

$(2)_TARGET_PREP  = $(STAMP_DIR)/$(1)-prep
$(2)_TARGET_PATCH = $(STAMP_DIR)/$(1)-patch
$(2)_TARGET_BUILD = $(STAMP_DIR)/$(1)-build
$(2)_TARGET_SAVE  = $(STAMP_DIR)/$(1)-save 

$(2)_FULL_VERSION:= $(shell cd $($(2)_GIT_REPO) && $(BASE_DIR)/tools/branch-tool version)
$(2)_OUTPUT = $(OUTPUT_DIR)/hadoop-$$($(2)_FULL_VERSION).tar.gz

# Step 1
# TODO: md5
$(DL_DIR)/$($2_SOURCE):
	wget -P $(DL_DIR) --progress bar $($(2)_SITE)/$($(2)_SOURCE)

# Step 2
$$($(2)_TARGET_PREP):
	mkdir -p $$($(2)_BUILD_DIR)
	$(BASE_DIR)/tools/setup-package-build $($(2)_GIT_REPO) $($(2)_BASE_REF) $($(2)_BUILD_REF) $(DL_DIR)/$($(2)_SOURCE) $$($(2)_BUILD_DIR)
	touch $$@

# Step 3
$$($(2)_TARGET_PATCH):
	$$($(2)_BUILD_DIR)/cloudera/apply-patches $$($(2)_BUILD_DIR) $$($(2)_BUILD_DIR)/cloudera/patches
	touch $$@

# Step 4
$$($(2)_TARGET_BUILD):
	/usr/bin/env JAVA32_HOME=$(JAVA32_HOME) JAVA64_HOME=$(JAVA64_HOME) JAVA5_HOME=$(JAVA5_HOME) FORREST_HOME=$(FORREST_HOME) $$($(2)_BUILD_DIR)/cloudera/do-release-build
	touch $$@

# Step 5
# TODO: don't make this hadoop specific
$$($(2)_TARGET_SAVE):
	cp -f $$($(2)_BUILD_DIR)/build/hadoop-$$($(2)_FULL_VERSION).tar.gz $(OUTPUT_DIR)
	touch $$@

# We download target when the source is not in the download directory
$(1)-download: $(DL_DIR)/$$($(2)_SOURCE) 

# To prep target, we need to download it first
$(1)-prep: $(1)-download $$($(2)_TARGET_PREP)

# To patch target, we need to prep it first
$(1)-patch: $(1)-prep $$($(2)_TARGET_PATCH)
 
# To build target, we need to patch it first
$(1)-build: $(1)-patch $$($(2)_TARGET_BUILD)

# To save target, we need to build it first
$(1)-save: $(1)-build $$($(2)_TARGET_SAVE)

$(1): $(1)-save

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
