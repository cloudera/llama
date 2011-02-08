CDH_VERSION=3

# CMF
CMF_BASE_VERSION=1.0
CMF_FULL_VERSION=1.0_SNAPSHOT
CMF_PKG_VERSION=$(CMF_FULL_VERSION)
CMF_NAME=cmf
CMF_PKG_NAME=cmf
CMF_BASE_REF=master
CMF_BUILD_REF=cdh-package
CMF_TARBALL_DST=cmf-$(CMF_BASE_REF).tar.gz
CMF_TARBALL_SRC=$(CMF_TARBALL_DST)
CMF_GIT_REPO=$(REPO_DIR)/cmf/cmf
CMF_PACKAGE_GIT_REPO=$(REPO_DIR)/cmf/cmf-package
CMF_SITE=http://git.sf.cloudera.com/index.cgi/cmf.git/snapshot/
$(eval $(call PACKAGE,cmf,CMF))

$(CMF_TARGET_PREP):
	-rm -rf $(PKG_BUILD_DIR)/rpm/
	mkdir -p $(PKG_BUILD_DIR)/rpm/
	cp -r $($(PKG)_PACKAGE_GIT_REPO)/rpm/topdir $(PKG_BUILD_DIR)/rpm
	mkdir -p $(PKG_BUILD_DIR)/rpm/topdir/{INSTALL,SOURCES,BUILD}
	cp $($(PKG)_OUTPUT_DIR)/$($(PKG)_NAME)-$($(PKG)_FULL_VERSION).tar.gz $(PKG_BUILD_DIR)/rpm/topdir/SOURCES
	$(BASE_DIR)/tools/create_rpms \
	  $($(PKG)_NAME) \
	  $(PKG_BUILD_DIR)/rpm/topdir/INSTALL \
	  $(PKG_BUILD_DIR)/rpm/topdir \
	  $($(PKG)_BASE_VERSION) \
	  $($(PKG)_FULL_VERSION) \
	  $($(PKG)_PKG_VERSION) \
	  $($(PKG)_RELEASE) 
	cp $(PKG_BUILD_DIR)/rpm/topdir/SRPMS/$($(PKG)_PKG_NAME)-$($(PKG)_PKG_VERSION)-$($(PKG)_RELEASE).src.rpm \
	   $($(PKG)_OUTPUT_DIR)
	touch $@
