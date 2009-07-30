define SRPM
-rm -rf $(PKG_BUILD_DIR)/rpm/
mkdir -p $(PKG_BUILD_DIR)/rpm/
cp -r $($(PKG)_PACKAGE_GIT_REPO)/rpm/topdir $(PKG_BUILD_DIR)/rpm
mkdir -p $(PKG_BUILD_DIR)/rpm/topdir/INSTALL
mkdir -p $(PKG_BUILD_DIR)/rpm/topdir/SOURCES
cp $(PKG_BUILD_DIR)/build/$($(PKG)_NAME)-$(PKG_FULL_VERSION).tar.gz $(PKG_BUILD_DIR)/rpm/topdir/SOURCES
$($(PKG)_PACKAGE_GIT_REPO)/rpm/create_rpms $($(PKG)_NAME) $(PKG_BUILD_DIR)/rpm/topdir/INSTALL $(PKG_BUILD_DIR)/rpm/topdir $($(PKG)_BASE_VERSION) $(PKG_FULL_VERSION)
endef
