# Implicit targets
SHELL := /bin/bash

# Download
$(BUILD_DIR)/%/.download:
	mkdir -p $(@D)
	[ -z "$($(PKG)_TARBALL_SRC)" -o -f $($(PKG)_DOWNLOAD_DST) ] || (cd $(DL_DIR) && curl -k -# -L -o $($(PKG)_TARBALL_DST) $($(PKG)_DOWNLOAD_URL))
	touch $@

# Prep
$(BUILD_DIR)/%/.prep:
	mkdir -p $($(PKG)_SOURCE_DIR)
	[ -z "$($(PKG)_TARBALL_SRC)" ] || $(BASE_DIR)/tools/setup-package-build \
	  $($(PKG)_GIT_REPO) \
	  $($(PKG)_BASE_REF) \
	  $($(PKG)_BUILD_REF) \
	  $($(PKG)_DOWNLOAD_DST) \
	  $($(PKG)_SOURCE_DIR) \
	  $($(PKG)_FULL_VERSION) \
	  $($(PKG)_SRC_PREFIX)
	# Special logic below for cases with source we want to copy, but without pristine tarballs.
	#[ -z "$($(PKG)_TARBALL_SRC)" -a -n "$($(PKG)_TARBALL_DST)" ] && cp -r $($(PKG)_GIT_REPO)/* $($(PKG)_SOURCE_DIR)
	touch $@

# Patch
$(BUILD_DIR)/%/.patch:
	[ -z "$($(PKG)_TARBALL_SRC)" ] || $($(PKG)_SOURCE_DIR)/cloudera/apply-patches \
	  $($(PKG)_SOURCE_DIR) \
	  $($(PKG)_SOURCE_DIR)/cloudera/patches
	touch $@

# Build
$(BUILD_DIR)/%/.build:
	# Hack - use tarball dst here because we want to do the build for CM, but not for bigtop-utils.
	cd $($(PKG)_SOURCE_DIR) ;\
	[ -z "$($(PKG)_TARBALL_DST)" ] || /usr/bin/env \
	  -u DISPLAY \
	  JAVA32_HOME=$(JAVA32_HOME) \
	  JAVA64_HOME=$(JAVA64_HOME) \
	  JAVA5_HOME=$(JAVA5_HOME) \
	  FORREST_HOME=$(FORREST_HOME) \
	  THRIFT_HOME=$(THRIFT_HOME) \
	  NODE_HOME=$(NODE_HOME) \
	  GIT_REPO=$($(PKG)_GIT_REPO) \
	  FULL_VERSION=$($(PKG)_FULL_VERSION) \
	  $($(PKG)_PACKAGE_GIT_REPO)/common/$($(PKG)_NAME)/do-component-build
	mkdir -p $($(PKG)_OUTPUT_DIR)
	[ -f "$($(PKG)_SOURCE_DIR)/build/$($(PKG)_NAME)-$($(PKG)_FULL_VERSION).tar.gz" ] && \
	  cp $($(PKG)_SOURCE_DIR)/build/$($(PKG)_NAME)-$($(PKG)_FULL_VERSION).tar.gz $($(PKG)_OUTPUT_DIR)
	touch $@

# Make source RPMs
$(BUILD_DIR)/%/.srpm:
	-rm -rf $(PKG_BUILD_DIR)/rpm/
	mkdir -p $(PKG_BUILD_DIR)/rpm/{INSTALL,SOURCES,BUILD,SRPMS,RPMS}
	cp -r $($(PKG)_PACKAGE_GIT_REPO)/rpm/$($(PKG)_NAME)/* $(PKG_BUILD_DIR)/rpm
	cp -r $($(PKG)_PACKAGE_GIT_REPO)/common/$($(PKG)_NAME)/* $(PKG_BUILD_DIR)/rpm/SOURCES
	# FIXME: BIGTOP-292
	cd $($(PKG)_PACKAGE_GIT_REPO)/common/$($(PKG)_NAME) ; tar -czf $(PKG_BUILD_DIR)/rpm/SOURCES/$($(PKG)_NAME)-bigtop-packaging.tar.gz *
	[ -f "$($(PKG)_OUTPUT_DIR)/$($(PKG)_NAME)-$($(PKG)_FULL_VERSION).tar.gz" ] && cp $($(PKG)_OUTPUT_DIR)/$($(PKG)_NAME)-$($(PKG)_FULL_VERSION).tar.gz $(PKG_BUILD_DIR)/rpm/SOURCES
	[ -d $($(PKG)_PACKAGE_GIT_REPO)/common/$($(PKG)_NAME) ] && \
		cp -r $($(PKG)_PACKAGE_GIT_REPO)/common/$($(PKG)_NAME)/* $(PKG_BUILD_DIR)/rpm/SOURCES
	sed -i -e '1i\
%define $(subst -,_,$($(PKG)_NAME))_version $($(PKG)_PKG_VERSION)$(CDH_BUILD_STAMP) \
%define $(subst -,_,$($(PKG)_NAME))_patched_version $($(PKG)_FULL_VERSION) \
%define $(subst -,_,$($(PKG)_NAME))_base_version $($(PKG)_BASE_VERSION) \
%define $(subst -,_,$($(PKG)_NAME))_release $($(PKG)_RELEASE_VERSION)' $(PKG_BUILD_DIR)/rpm/SPECS/$($(PKG)_NAME).spec
	rpmbuild --define "_topdir $(PKG_BUILD_DIR)/rpm/" -bs --nodeps --buildroot="$(PKG_BUILD_DIR)/rpm/INSTALL" \
                                                                       $(PKG_BUILD_DIR)/rpm/SPECS/$($(PKG)_NAME).spec
	cp $(PKG_BUILD_DIR)/rpm/SRPMS/$($(PKG)_PKG_NAME)-$($(PKG)_PKG_VERSION)$(CDH_BUILD_STAMP)-$($(PKG)_RELEASE).src.rpm \
	   $($(PKG)_OUTPUT_DIR)
	touch $@

# Make binary RPMs
$(BUILD_DIR)/%/.rpm: SRCRPM=$($(PKG)_OUTPUT_DIR)/$($(PKG)_PKG_NAME)-$($(PKG)_PKG_VERSION)$(CDH_BUILD_STAMP)-$($(PKG)_RELEASE).src.rpm
$(BUILD_DIR)/%/.rpm:
	rpmbuild --define "_topdir $(PKG_BUILD_DIR)/rpm/" --rebuild $(SRCRPM)
	touch $@

# Make source DEBs
$(BUILD_DIR)/%/.sdeb:
	-rm -rf $(PKG_BUILD_DIR)/deb/
	mkdir -p $(PKG_BUILD_DIR)/deb/
	cd $(PKG_BUILD_DIR)/deb && \
          mkdir $($(PKG)_NAME)-$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP) ;\
	  if [ -n "$($(PKG)_TARBALL_SRC)" ]; then \
	    cp $($(PKG)_OUTPUT_DIR)/$($(PKG)_NAME)-$(PKG_FULL_VERSION).tar.gz \
	       $(PKG_BUILD_DIR)/deb/$($(PKG)_PKG_NAME)_$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP).orig.tar.gz ;\
	    tar -C $($(PKG)_NAME)-$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP) --strip-components 1 \
                -xzvf $($(PKG)_PKG_NAME)_$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP).orig.tar.gz  ;\
	  else \
            tar -czf $(PKG_BUILD_DIR)/deb/$($(PKG)_PKG_NAME)_$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP).orig.tar.gz \
                  $($(PKG)_NAME)-$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP) ;\
          fi ;\
	cd $(PKG_BUILD_DIR)/deb/$($(PKG)_NAME)-$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP) && \
          cp -r $($(PKG)_PACKAGE_GIT_REPO)/deb/$($(PKG)_NAME) debian && \
	  sed -i -e '/^#!/a\
$(PKG)_VERSION=$($(PKG)_PKG_VERSION)$(CDH_BUILD_STAMP) \
$(PKG)_PATCHED_VERSION=$($(PKG)_FULL_VERSION) \
$(PKG)_BASE_VERSION=$($(PKG)_BASE_VERSION) \
$(PKG)_RELEASE=$($(PKG)_RELEASE_VERSION)' debian/rules && \
	  cp -r $($(PKG)_PACKAGE_GIT_REPO)/common/$($(PKG)_NAME)/* debian && \
	  find debian -name "*.[ex,EX,~]" | xargs rm -f && \
	  $(BASE_DIR)/tools/generate-debian-changelog \
	    $($(PKG)_GIT_REPO) \
	    $($(PKG)_BASE_REF) \
	    $($(PKG)_BUILD_REF) \
	    $($(PKG)_PKG_NAME) \
	    $($(PKG)_RELEASE) \
	    debian/changelog \
	    $($(PKG)_PKG_VERSION)$(CDH_BUILD_STAMP) && \
	  dpkg-buildpackage -uc -us -sa -S
	for file in $($(PKG)_PKG_NAME)_$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP)-$($(PKG)_RELEASE).dsc \
                    $($(PKG)_PKG_NAME)_$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP)-$($(PKG)_RELEASE).diff.gz \
                    $($(PKG)_PKG_NAME)_$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP)-$($(PKG)_RELEASE)_source.changes \
                    $($(PKG)_PKG_NAME)_$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP).orig.tar.gz ; \
            do cp $(PKG_BUILD_DIR)/deb/$$file $($(PKG)_OUTPUT_DIR); \
        done
	touch $@

$(BUILD_DIR)/%/.deb: SRCDEB=$($(PKG)_PKG_NAME)_$($(PKG)_PKG_VERSION)$(CDH_BUILD_STAMP)-$($(PKG)_RELEASE).dsc GIT_REPO=$($(PKG)_GIT_REPO)
$(BUILD_DIR)/%/.deb:
	cd $($(PKG)_OUTPUT_DIR) && \
		dpkg-source -x $(SRCDEB) && \
		cd $($(PKG)_PKG_NAME)-$(PKG_PKG_VERSION)$(CDH_BUILD_STAMP) && \
			debuild \
				--preserve-envvar PATH --preserve-envvar JAVA32_HOME --preserve-envvar JAVA64_HOME \
				--preserve-envvar JAVA5_HOME --preserve-envvar FORREST_HOME --preserve-envvar MAVEN3_HOME \
				--preserve-envvar THRIFT_HOME --preserve-envvar JAVA_HOME --preserve-envvar GIT_REPO \
				--preserve-envvar NODE_HOME -uc -us -b
	touch $@

$(BUILD_DIR)/%/.relnotes:  $($(PKG)_OUTPUT_DIR)/$($(PKG)_NAME)-$($(PKG)_PKG_VERSION).releasenotes.html
$(BUILD_DIR)/%/.relnotes:
	mkdir -p $(PKG_BUILD_DIR)
	$(BASE_DIR)/tools/relnotes/relnote-gen.sh \
		$($(PKG)_OUTPUT_DIR) \
		$($(PKG)_GIT_REPO) \
		"$($(PKG)_BASE_REF)..HEAD" \
		"CDH $(CDH_VERSION)" \
		"$($(PKG)_BASE_VERSION)" \
		"$($(PKG)_NAME)-$($(PKG)_PKG_VERSION)" \
		"$($(PKG)_RELNOTES_NAME)" \
		"$($(PKG)_PACKAGE_GIT_REPO)" \
		"$($(PKG)_NAME)" \
		"$(PREV_RELEASE_TAG)..HEAD"
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
$(2)_FULL_VERSION   = $$($(2)_BASE_VERSION)-$(CDH_VERSION_STRING)
$(2)_PKG_VERSION   := $(shell cd $($(2)_GIT_REPO) && $(BASE_DIR)/tools/branch-tool version --prefix=$(CDH) $(NO_PATCH_COUNT))
$(2)_BUILD_REF      := $(notdir $(shell cd $($(2)_GIT_REPO) && git symbolic-ref --quiet HEAD))

$(2)_BUILD_DIR      = $(BUILD_DIR)/$(CDH)/$(1)/$$($(2)_FULL_VERSION)/
$(2)_OUTPUT_DIR      = $(OUTPUT_DIR)/$(CDH)/$(1)
$(2)_SOURCE_DIR       = $$($(2)_BUILD_DIR)/source

# Download source URL and destination path
$(2)_DOWNLOAD_URL = $($(2)_SITE)/$($(2)_TARBALL_SRC)
$(2)_DOWNLOAD_DST = $(DL_DIR)/$($(2)_TARBALL_DST)

# Define the file stamps
$(2)_TARGET_DL       = $$($(2)_BUILD_DIR)/.download
$(2)_TARGET_PREP     = $$($(2)_BUILD_DIR)/.prep
$(2)_TARGET_PATCH    = $$($(2)_BUILD_DIR)/.patch
$(2)_TARGET_BUILD    = $$($(2)_BUILD_DIR)/.build
$(2)_TARGET_SRPM     = $$($(2)_BUILD_DIR)/.srpm
$(2)_TARGET_RPM      = $$($(2)_BUILD_DIR)/.rpm
$(2)_TARGET_SDEB     = $$($(2)_BUILD_DIR)/.sdeb
$(2)_TARGET_DEB      = $$($(2)_BUILD_DIR)/.deb
$(2)_TARGET_RELNOTES = $$($(2)_BUILD_DIR)/.relnotes

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

# To make debs, we need to make source packages
$(1)-deb: $(1)-sdeb $$($(2)_TARGET_DEB)

# To make the release notes we need to build the target
$(1)-relnotes: $$($(2)_TARGET_RELNOTES)

####
# Helper targets -version -help etc
$(1)-version:
	@echo "Base: $$($(2)_BASE_VERSION)"
	@echo "Full: $$($(2)_FULL_VERSION)"

$(1)-help:
	@echo "    $(1)  [$(1)-version, $(1)-info, $(1)-relnotes,"
	@echo "           $(1)-srpm, $(1)-rpm]"
	@echo "           $(1)-sdeb, $(1)-deb]"

$(1)-clean:
	-rm -rf $(BUILD_DIR)/$(CDH)/$(1)

$(1)-info:
	@echo "Info for package $(1)"
	@echo "  Will download from URL: $$($(2)_DOWNLOAD_URL)"
	@echo "  To destination file: $$($(2)_DOWNLOAD_DST)"
	@echo "  Then unpack into $$($(2)_SOURCE_DIR)"
	@echo
	@echo "Patches:"
	@echo "  BASE_REF: $$($(2)_BASE_REF)"
	@echo "  BUILD_REF: $$($(2)_BUILD_REF)"
	@echo "  Generated from: git log $$($(2)_BASE_REF)..$$($(2)_BUILD_REF) in $$($(2)_GIT_REPO)"
	@echo
	@echo "Git repo: " $$($(2)_GIT_REPO)
	@echo "Currently checked out: " $(shell cd $($(2)_GIT_REPO) && git symbolic-ref --quiet HEAD)
	@echo "Version: $$($(2)_FULL_VERSION)"
	@echo
	@echo "Stamp status:"
	@for mystamp in DL PREP PATCH BUILD SRPM RPM SDEB DEB RELNOTES;\
	  do echo -n "  $$$$mystamp: " ; \
	  ([ -f $($(1)_$$$$mystamp) ] && echo present || echo not present) ; \
	done

# Implicit rules with PKG variable
$$($(2)_TARGET_DL):       PKG=$(2)
$$($(2)_TARGET_PREP):     PKG=$(2)
$$($(2)_TARGET_PREP):     PKG_FULL_VERSION=$$($(2)_FULL_VERSION)
$$($(2)_TARGET_PATCH):    PKG=$(2)
$$($(2)_TARGET_BUILD):    PKG=$(2)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB) $$($(2)_TARGET_DEB) $$($(2)_TARGET_RELNOTES): PKG=$(2)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB) $$($(2)_TARGET_DEB) $$($(2)_TARGET_RELNOTES): PKG_FULL_VERSION=$$($(2)_FULL_VERSION)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB) $$($(2)_TARGET_DEB) $$($(2)_TARGET_RELNOTES): PKG_PKG_VERSION=$$($(2)_PKG_VERSION)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB) $$($(2)_TARGET_DEB) $$($(2)_TARGET_RELNOTES): PKG_SOURCE_DIR=$$($(2)_SOURCE_DIR)
$$($(2)_TARGET_RPM) $$($(2)_TARGET_SRPM) $$($(2)_TARGET_SDEB) $$($(2)_TARGET_DEB) $$($(2)_TARGET_RELNOTES): PKG_BUILD_DIR=$$($(2)_BUILD_DIR)


TARGETS += $(1)
TARGETS_HELP += $(1)-help
TARGETS_CLEAN += $(1)-clean
TARGETS_SRPM += $(1)-srpm
TARGETS_RPM += $(1)-rpm
TARGETS_SDEB += $(1)-sdeb
TARGETS_DEB += $(1)-deb
TARGETS_RELNOTES += $(1)-relnotes
endef
