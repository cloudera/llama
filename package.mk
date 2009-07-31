# Implicit targets

# Download 
$(BUILD_DIR)/%/.download:
	mkdir -p $(@D)
	[ -f $(DL_DIR)/$($(PKG)_SOURCE) ] || wget -P $(DL_DIR) --progress bar $($(PKG)_SITE)/$($(PKG)_SOURCE)
	touch $@

# Prep
$(BUILD_DIR)/%/.prep:
	mkdir -p $($(PKG)_SOURCE_DIR)
	$(BASE_DIR)/tools/setup-package-build $($(PKG)_GIT_REPO) $($(PKG)_BASE_REF) $($(PKG)_BUILD_REF) $(DL_DIR)/$($(PKG)_SOURCE) $($(PKG)_SOURCE_DIR)
	touch $@

# Patch
$(BUILD_DIR)/%/.patch:
	$($(PKG)_SOURCE_DIR)/cloudera/apply-patches $($(PKG)_SOURCE_DIR) $($(PKG)_SOURCE_DIR)/cloudera/patches
	touch $@

# Build
$(BUILD_DIR)/%/.build: 
	/usr/bin/env JAVA32_HOME=$(JAVA32_HOME) JAVA64_HOME=$(JAVA64_HOME) JAVA5_HOME=$(JAVA5_HOME) FORREST_HOME=$(FORREST_HOME) $($(PKG)_SOURCE_DIR)/cloudera/do-release-build
	cp $($(PKG)_SOURCE_DIR)/build/$($(PKG)_NAME)-$($(PKG)_FULL_VERSION).tar.gz $(OUTPUT_DIR)
	touch $@

# Make source RPMs
$(BUILD_DIR)/%/.srpm:
	-rm -rf $(PKG_BUILD_DIR)/rpm/
	mkdir -p $(PKG_BUILD_DIR)/rpm/
	cp -r $($(PKG)_PACKAGE_GIT_REPO)/rpm/topdir $(PKG_BUILD_DIR)/rpm
	mkdir -p $(PKG_BUILD_DIR)/rpm/topdir/INSTALL
	mkdir -p $(PKG_BUILD_DIR)/rpm/topdir/SOURCES
	cp $(OUTPUT_DIR)/$($(PKG)_NAME)-$($(PKG)_FULL_VERSION).tar.gz $(PKG_BUILD_DIR)/rpm/topdir/SOURCES
	$($(PKG)_PACKAGE_GIT_REPO)/rpm/create_rpms $($(PKG)_NAME) $(PKG_BUILD_DIR)/rpm/topdir/INSTALL $(PKG_BUILD_DIR)/rpm/topdir $($(PKG)_BASE_VERSION) $(PKG_FULL_VERSION)
	cp $(PKG_BUILD_DIR)/rpm/topdir/SRPMS/$($(PKG)_PKG_NAME)-$($(PKG)_FULL_VERSION)-$($(PKG)_RELEASE).src.rpm $(OUTPUT_DIR)
	touch $@

# Make binary RPMs
$(BUILD_DIR)/%/.rpm: SRCRPM=$(OUTPUT_DIR)/$($(PKG)_PKG_NAME)-$($(PKG)_FULL_VERSION)-$($(PKG)_RELEASE).src.rpm
$(BUILD_DIR)/%/.rpm:
	rpmbuild --rebuild $(SRCRPM)
	rpmbuild --rebuild --target noarch $(SRCRPM)
	touch $@

# Make source DEBs
$(BUILD_DIR)/%/.sdeb:
	-rm -rf $(PKG_BUILD_DIR)/deb/
	mkdir -p $(PKG_BUILD_DIR)/deb/
	cp $(OUTPUT_DIR)/$($(PKG)_NAME)-$(PKG_FULL_VERSION).tar.gz $(PKG_BUILD_DIR)/deb/$($(PKG)_PKG_NAME)_$(PKG_FULL_VERSION).orig.tar.gz
	cd $(PKG_BUILD_DIR)/deb && tar -xvf $($(PKG)_PKG_NAME)_$(PKG_FULL_VERSION).orig.tar.gz  && cd $($(PKG)_NAME)-$(PKG_FULL_VERSION) && \
	cp -r $($(PKG)_PACKAGE_GIT_REPO)/deb/debian.$($(PKG)_NAME) debian && \
	find debian -name "*.[ex,EX,~]" | xargs rm -f && \
	$(BASE_DIR)/tools/generate-debian-changelog $($(PKG)_GIT_REPO) $($(PKG)_BASE_REF) $($(PKG)_BUILD_REF) $($(PKG)_PKG_NAME) debian/changelog && \
	dpkg-buildpackage -uc -us -sa -S 
	for file in $($(PKG)_PKG_NAME)_$(PKG_FULL_VERSION)-1cdh.dsc \
                    $($(PKG)_PKG_NAME)_$(PKG_FULL_VERSION)-1cdh.diff.gz \
                    $($(PKG)_PKG_NAME)_$(PKG_FULL_VERSION)-1cdh_source.changes \
                    $($(PKG)_PKG_NAME)_$(PKG_FULL_VERSION).orig.tar.gz ; \
            do cp $(PKG_BUILD_DIR)/deb/$$file $(OUTPUT_DIR); \
        done
	touch $@
	

# Package make function
# $1 is the target prefix, $2 is the variable prefix
define PACKAGE

# The default PKG_NAME will be the target prefix
$(2)_NAME           ?= $(1)

# For deb packages, the name of the package itself
$(2)_PKG_NAME       ?= $$($(2)_NAME)

# The default PKG_RELEASE will be 1 unless specified
$(2)_RELEASE        ?= 1

# Calculate the full version based on the git patches
$(2)_FULL_VERSION   := $(shell cd $($(2)_GIT_REPO) && $(BASE_DIR)/tools/branch-tool version)

$(2)_BUILD_DIR      = $(BUILD_DIR)/$(1)/$$($(2)_FULL_VERSION)/
$(2)_SOURCE_DIR       = $$($(2)_BUILD_DIR)/source

# Define the file stamps
$(2)_TARGET_DL       = $$($(2)_BUILD_DIR)/.download
$(2)_TARGET_PREP     = $$($(2)_BUILD_DIR)/.prep
$(2)_TARGET_PATCH    = $$($(2)_BUILD_DIR)/.patch
$(2)_TARGET_BUILD    = $$($(2)_BUILD_DIR)/.build
$(2)_TARGET_SRPM     = $$($(2)_BUILD_DIR)/.srpm
$(2)_TARGET_RPM      = $$($(2)_BUILD_DIR)/.rpm
$(2)_TARGET_SDEB     = $$($(2)_BUILD_DIR)/.sdeb

# We download target when the source is not in the download directory
$(1)-download: $$($(2)_TARGET_DL) 

# To prep target, we need to download it first
$(1)-prep: $(1)-download $$($(2)_TARGET_PREP)

# To patch target, we need to prep it first
$(1)-patch: $(1)-prep $$($(2)_TARGET_PATCH)
 
# To build target, we need to patch it first
$(1): $(1)-patch $$($(2)_TARGET_BUILD) $$($(2)_HOOK_POST_BUILD)

# To make srpms, we need to build the package
$(1)-srpm: $(1) $$($(2)_TARGET_SRPM)

# To make binary rpms, we need to build source RPMs
$(1)-rpm: $(1)-srpm $$($(2)_TARGET_RPM)

# To make sdebs, we need to build the package
$(1)-sdeb: $(1) $$($(2)_TARGET_SDEB)

#### 
# Helper targets -version -help etc
$(1)-version:
	@echo "Base: $$($(2)_BASE_VERSION)"
	@echo "Full: $$($(2)_FULL_VERSION)"

$(1)-help:
	@echo "    $(1)  [$(1)-version, $(1)-srpm, $(1)-sdeb]"

$(1)-clean: 
	-rm -rf $(BUILD_DIR)/$(1)

# Implicit rules with PKG variable
$$($(2)_TARGET_DL):       PKG=$(2)
$$($(2)_TARGET_PREP):     PKG=$(2)
$$($(2)_TARGET_PATCH):    PKG=$(2)
$$($(2)_TARGET_BUILD):    PKG=$(2)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB): PKG=$(2)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB): PKG_FULL_VERSION=$$($(2)_FULL_VERSION)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB): PKG_SOURCE_DIR=$$($(2)_SOURCE_DIR)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB): PKG_BUILD_DIR=$$($(2)_BUILD_DIR)

TARGETS += $(1) 
TARGETS_HELP += $(1)-help
TARGETS_CLEAN += $(1)-clean
endef
