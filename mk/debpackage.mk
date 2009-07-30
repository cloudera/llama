define SDEB
-rm -rf $(PKG_BUILD_DIR)/deb/
mkdir -p $(PKG_BUILD_DIR)/deb/
cp $(PKG_BUILD_DIR)/build/hadoop-$(PKG_FULL_VERSION).tar.gz $(PKG_BUILD_DIR)/deb/hadoop-$(PKG_FULL_VERSION).orig.tar.gz
cd $(PKG_BUILD_DIR)/deb && tar -xvf hadoop-$(PKG_FULL_VERSION).orig.tar.gz
endef
