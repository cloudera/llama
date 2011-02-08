CDH_VERSION=3

# CMF
CMF_BASE_VERSION=1.0_SNAPSHOT
CMF_NAME=cmf
CMF_PKG_NAME=cmf
CMF_BASE_REF=master
CMF_BUILD_REF=cdh-$(CMF_BASE_VERSION)
CMF_TARBALL_DST=cmf-$(CMF_BASE_REF).tar.gz
CMF_TARBALL_SRC=$(CMF_TARBALL_DST)
CMF_GIT_REPO=$(REPO_DIR)/cmf/cmf
CMF_PACKAGE_GIT_REPO=$(REPO_DIR)/cmf/cmf-package
CMF_SITE=http://git.sf.cloudera.com/index.cgi/cmf.git/snapshot/
$(eval $(call PACKAGE,cmf,CMF))
