#
# Function for building Debian source packages
# TODO: remove the "hadoop" references in order to make way for pig, hive, etc
#
define SDEB
-rm -rf $(PKG_BUILD_DIR)/deb/
mkdir -p $(PKG_BUILD_DIR)/deb/
cp $(PKG_BUILD_DIR)/build/hadoop-$(PKG_FULL_VERSION).tar.gz $(PKG_BUILD_DIR)/deb/hadoop-$(PKG_FULL_VERSION).orig.tar.gz
cd $(PKG_BUILD_DIR)/deb && tar -xvf hadoop-$(PKG_FULL_VERSION).orig.tar.gz  && cd hadoop-$(PKG_FULL_VERSION) && \
cp -r $($(PKG)_PACKAGE_GIT_REPO)/deb/debian.hadoop debian && \
find debian -name "*.[ex,EX,~]" | xargs rm -f && \
$(BASE_DIR)/tools/generate-debian-changelog $($(PKG)_GIT_REPO) $($(PKG)_BASE_REF) $($(PKG)_BUILD_REF) hadoop debian/changelog && \
dpkg-buildpackage -uc -us -sa -S 
endef
