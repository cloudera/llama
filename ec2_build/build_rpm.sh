#!/bin/sh -x
# (c) Copyright 2009 Cloudera, Inc.
set -e

##SUBSTITUTE_VARS##

export AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY

cd /tmp

# fetch deb parts of manifest
curl -s $MANIFEST_URL | grep ^rpm > manifest.txt

# download all the files
perl -n -a -e '
if (/^rpm/) {
  print "Fetching $F[1]...\n";
  system("/usr/bin/curl", "-s", "-o", $F[1], $F[3]);
}' manifest.txt

# Some package deps
wget http://download.fedora.redhat.com/pub/epel/5/i386/epel-release-5-3.noarch.rpm
rpm -Uvh epel*.rpm
yum -y install rpm-build yum-utils zlib-devel gcc gcc-devel gcc-c++ gcc-c++-devel lzo-devel glibc-devel ant ant-nodeps ruby git

# Install s3cmd
wget http://s3.amazonaws.com/ServEdge_pub/s3sync/s3sync.tar.gz
tar xzvf s3sync.tar.gz
S3CMD=`pwd`/s3sync/s3cmd.rb

############### JAVA STUFF #######################

pushd /mnt # all these packages are going to be pretty big, possibly too big for /tmp
DEFAULT_JAVA_PATH=/usr/java/default
echo "export JAVA_HOME=$DEFAULT_JAVA_PATH" >> /etc/profile
export JAVA_HOME=$DEFAULT_JAVA_PATH
SECONDARY_JDK_PATH="/opt/java/jdk1.6.0_14" # i386 and x64 have the same path
mkdir -p $(dirname $SECONDARY_JDK_PATH)

# install the "main" java (from RPM) and the "secondary" java, not
# from RPM.
ARCH=`uname -i`
if [ "$ARCH" = "x86_64" ]; then
  MAIN_JDK_PACKAGE="jdk-6u14-linux-x64-rpm.bin"
  echo "export JAVA64_HOME=$DEFAULT_JAVA_PATH" >> /etc/profile
  export JAVA64_HOME=$DEFAULT_JAVA_PATH
  SECONDARY_JDK_PACKAGE="jdk-6u14-linux-i386.bin"
  echo "export JAVA32_HOME=$SECONDARY_JDK_PATH" >> /etc/profile
  export JAVA32_HOME=$SECONDARY_JDK_PATH
else
  MAIN_JDK_PACKAGE="jdk-6u14-linux-i386-rpm.bin"
  echo "export JAVA32_HOME=$DEFAULT_JAVA_PATH" >> /etc/profile
  export JAVA32_HOME=$DEFAULT_JAVA_PATH
  SECONDARY_JDK_PACKAGE="jdk-6u14-linux-x64.bin"
  echo "export JAVA64_HOME=$SECONDARY_JDK_PATH" >> /etc/profile
  export JAVA64_HOME=$SECONDARY_JDK_PATH
fi
JDK5_PACKAGE="jdk-1_5_0_19-linux-i386.bin"
JAVA5_HOME=/opt/java/jdk1.5.0_19
echo "export JAVA5_HOME=$JAVA5_HOME" >> /etc/profile
export JAVA5_HOME=$JAVA5_HOME


for pkg in $MAIN_JDK_PACKAGE $SECONDARY_JDK_PACKAGE $JDK5_PACKAGE
do
  $S3CMD get "cloudera-packages:$pkg" "$pkg"
  chmod a+x "$pkg"
done

# java wants to show you a license with more. Disable this so they can't
# interrupt our unattended installation
mv /bin/more /bin/more.no
yes | ./$MAIN_JDK_PACKAGE -noregister
yes | ./$SECONDARY_JDK_PACKAGE -noregister
yes | ./$JDK5_PACKAGE -noregister

mv $(basename $SECONDARY_JDK_PATH) $SECONDARY_JDK_PATH
mv $(basename $JAVA5_HOME) $JAVA5_HOME

export PATH=$DEFAULT_JAVA_PATH/bin:$PATH
echo "export PATH=$DEFAULT_JAVA_PATH/bin:\$PATH" >> /etc/profile

java -version
$SECONDARY_JDK_PATH/bin/java -version
$JAVA5_HOME/bin/java -version

mv /bin/more.no /bin/more

############# ANT ######################
# Note that the ant and ant-nodeps RPMs have already been installed via yum.  We need ant 1.7, though.
ANT_PACKAGE_NAME="apache-ant-1.7.1-bin.tar.gz"
ANT_DIR="apache-ant-1.7.1"
ANT_HOME=/opt/$ANT_DIR

$S3CMD get "cloudera-packages:$ANT_PACKAGE_NAME" "$ANT_PACKAGE_NAME"
tar xzf "$ANT_PACKAGE_NAME"
mv $ANT_DIR $ANT_HOME

export ANT_HOME=$ANT_HOME
echo "export ANT_HOME=$ANT_HOME" >> /etc/profile
export PATH=$ANT_HOME/bin:$PATH
echo "export PATH=$ANT_HOME/bin:\$PATH" >> /etc/profile

############## FORREST ####################

FORREST_PACKAGE_NAME="apache-forrest-0.8.tar.gz"
FORREST_DIR="apache-forrest-0.8"
FORREST_HOME=/opt/$FORREST_DIR

$S3CMD get "cloudera-packages:$FORREST_PACKAGE_NAME" "$FORREST_PACKAGE_NAME"
tar xzf "$FORREST_PACKAGE_NAME"
mv "$FORREST_DIR" "$FORREST_HOME"

export FORREST_HOME=$FORREST_HOME
echo "export FORREST_HOME=$FORREST_HOME" >> /etc/profile

chmod a+w "$FORREST_HOME"

popd

############# BUILD PACKAGE ####################

# Satisfy build deps
YUMINST="yum --nogpgcheck -y install"
rpm -qRp hadoop*src.rpm | awk '{print $1}' | xargs $YUMINST

# make build dir
rm -Rf /tmp/topdir
mkdir /tmp/topdir
(cd /tmp/topdir && mkdir BUILD RPMS SOURCE SPECS SRPMS)

for TARGET_ARCH in noarch $ARCH ; do
  rm -Rf /tmp/buildroot
  mkdir /tmp/buildroot
  rpmbuild --define "_topdir /tmp/topdir" --buildroot /tmp/buildroot --target $TARGET_ARCH --rebuild hadoop*src.rpm
done


############################## UPLOAD ##############################


CODENAME=$(lsb_release -is -r | tr 'A-Z' 'a-z' | sed -e 's/ //g')

for arch_dir in /tmp/topdir/RPMS/*  ; do
    TARGET_ARCH=$(basename $arch_dir)
    for f in $arch_dir/*.rpm ; do
        $S3CMD put $S3_BUCKET:build/$BUILD_ID/rpm_${CODENAME}_${ARCH}/$(basename $f) $f
    done
done

# If we're running on S3, shutdown the node
# (do the check so you can test elsewhere)
hostname -f | grep -q ec2.internal && shutdown -h now
