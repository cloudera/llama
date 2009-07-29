# Git Package make function
# $1 is the target prefix, $2 is the variable prefix
define gitpackage
$(2)_BUILD_DIR =   $(BUILD_DIR)/$(1)-build
$(2)_STAMP_PREP =  $(STAMP_DIR)/$(1)-prep
$(2)_STAMP_PATCH = $(STAMP_DIR)/$(1)-patch
$(2)_STAMP_BUILD = $(STAMP_DIR)/$(1)-build

$(DL_DIR)/$($2_SOURCE):
	wget -P $(DL_DIR) --progress bar $($(2)_SITE)/$($(2)_SOURCE)

$$($(2)_STAMP_PREP):
	mkdir -p $$($(2)_BUILD_DIR)
	$(BASE_DIR)/tools/setup-package-build $($(2)_GIT_REPO) $($(2)_BASE_REF) $($(2)_BUILD_REF) $(DL_DIR)/$($(2)_SOURCE) $$($(2)_BUILD_DIR)
	touch $$($(2)_STAMP_PREP)

$$($(2)_STAMP_PATCH):
	$$($(2)_BUILD_DIR)/cloudera/apply-patches $$($(2)_BUILD_DIR) $$($(2)_BUILD_DIR)/cloudera/patches
	touch $$($(2)_STAMP_PATCH) 

$$($(2)_STAMP_BUILD):
	/usr/bin/env JAVA32_HOME=$(JAVA32_HOME) JAVA64_HOME=$(JAVA64_HOME) JAVA5_HOME=$(JAVA5_HOME) FORREST_HOME=$(FORREST_HOME) $$($(2)_BUILD_DIR)/cloudera/do-release-build
	touch $$($(2)_STAMP_BUILD)

$(1)-download: $(DL_DIR)/$$($(2)_SOURCE) 

$(1)-prep: $(1)-download $$($(2)_STAMP_PREP)

$(1)-patch: $(1)-prep $$($(2)_STAMP_PATCH)

$(1)-clean: 
	-rm -f $(STAMP_DIR)/$(1)*
	-rm -rf $$($(2)_BUILD_DIR)

$(1): $(1)-patch $(STAMP_DIR)/$(1)-build
TARGETS += $(1) 
endef
