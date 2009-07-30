#
# Function for building Debian source packages
#
define SDEB
-rm -rf $(PKG_BUILD_DIR)/deb/
mkdir -p $(PKG_BUILD_DIR)/deb/
cp $(PKG_BUILD_DIR)/build/$($(PKG)_NAME)-$(PKG_FULL_VERSION).tar.gz $(PKG_BUILD_DIR)/deb/$($(PKG)_NAME)-$(PKG_FULL_VERSION).orig.tar.gz
cd $(PKG_BUILD_DIR)/deb && tar -xvf $($(PKG)_NAME)-$(PKG_FULL_VERSION).orig.tar.gz  && cd $($(PKG)_NAME)-$(PKG_FULL_VERSION) && \
cp -r $($(PKG)_PACKAGE_GIT_REPO)/deb/debian.$($(PKG)_NAME) debian && \
find debian -name "*.[ex,EX,~]" | xargs rm -f && \
$(BASE_DIR)/tools/generate-debian-changelog $($(PKG)_GIT_REPO) $($(PKG)_BASE_REF) $($(PKG)_BUILD_REF) $($(PKG)_NAME) debian/changelog && \
dpkg-buildpackage -uc -us -sa -S 
endef
