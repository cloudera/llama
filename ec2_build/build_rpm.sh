#!/bin/sh -x
# (c) Copyright 2009 Cloudera, Inc.
set -e

##SUBSTITUTE_VARS##
export BUILD_ID
export CDH_RELEASE
export INTERACTIVE
export BUILD_USER
export CODENAME
export MANIFEST_URL
export PACKAGES
export S3_BUCKET
export AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY

export ANT_HOME=/usr/local/share/apache-ant-1.7.1
export PATH=$ANT_HOME/bin:$PATH

export JAVA5_HOME=/usr/java/jdk1.5.0_18
export JAVA32_HOME=/usr/java/jdk1.6.0_14_i386
export JAVA_HOME=$JAVA32_HOME

export ARCH=$(uname -i)

if [ "$ARCH" == "x64_86" ]; then
  export JAVA64_HOME=/usr/java/jdk1.6.0_14
  export JAVA_HOME=$JAVA64_HOME
fi

export FORREST_HOME=/usr/local/share/apache-forrest-0.8

chmod a+w "$FORREST_HOME"

function copy_logs_s3 {
      s3cmd.rb put $S3_BUCKET:build/$BUILD_ID/${CODENAME}-${ARCH}-user.log /tmp/log.txt
}

if [ "x$INTERACTIVE" == "xFalse" ]; then
  trap "copy_logs_s3; hostname -f | grep -q ec2.internal && shutdown -h now;" INT TERM EXIT
fi

rm -rf /usr/local/share/apache-forrest-0.8/build/plugins # build fails with this symlink in place, so remove it

yum -y install rpm-build yum-utils zlib-devel gcc gcc-devel gcc-c++ gcc-c++-devel lzo-devel glibc-devel ant ant-nodeps ruby git libtool

############# BUILD PACKAGE ####################

for PACKAGE in $PACKAGES; do
 
  mkdir /tmp/$BUILD_ID
  pushd /tmp/$BUILD_ID

  # fetch deb parts of manifest
  curl -s $MANIFEST_URL | grep ^$PACKAGE > manifest.txt

  # download all the files
  perl -n -a -e "
  if (/^$PACKAGE-rpm/) {
    print \"Fetching \$F[1]...\\n\";
    system(\"/usr/bin/curl\", \"-s\", \"-o\", \$F[1], \$F[3]);
  }" manifest.txt

  # Satisfy build deps
  YUMINST="yum --nogpgcheck -y install"
  rpm -qRp *src.rpm | awk '{print $1}' | xargs $YUMINST

  # make build dir
  rm -Rf /tmp/topdir
  mkdir /tmp/topdir
  (cd /tmp/topdir && mkdir BUILD RPMS SOURCE SPECS SRPMS)

  for TARGET_ARCH in noarch $ARCH ; do
    rm -Rf /tmp/buildroot
    mkdir /tmp/buildroot
    rpmbuild --define "_topdir /tmp/topdir" --buildroot /tmp/buildroot --target $TARGET_ARCH --rebuild *src.rpm
  done

  ############################## UPLOAD ##############################

  for arch_dir in /tmp/topdir/RPMS/*  ; do
    TARGET_ARCH=$(basename $arch_dir)
    for f in $arch_dir/*.rpm ; do
        s3cmd.rb put $S3_BUCKET:build/$BUILD_ID/rpm_${CODENAME}_${ARCH}/$(basename $f) $f
    done
  done

  # Leave /tmp/$BUILD_ID
  popd

  rm -rf /tmp/$BUILD_ID

done

# Untrap, we're shutting down directly from here so the exit trap probably won't
# have time to do anything
if [ "x$INTERACTIVE" == "xFalse" ]; then
  trap - INT TERM EXIT
fi

copy_logs_s3

# If we're running on S3, shutdown the node
# (do the check so you can test elsewhere)
hostname -f | grep -q ec2.internal && shutdown -h now
