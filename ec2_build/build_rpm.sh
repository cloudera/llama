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
yum -y install rpm-build yum-utils ruby gcc-c++

# Install s3cmd
OLDDIR=`pwd`
cd /tmp
  wget http://s3.amazonaws.com/ServEdge_pub/s3sync/s3sync.tar.gz
  tar xzvf s3sync.tar.gz
  S3CMD=`pwd`/s3sync/s3cmd.rb
cd $OLDDIR

############### JAVA STUFF #######################

# install jdk
ARCH=`uname -i`
if [ "$ARCH" = "x86_64" ]; then
  JDK_PACKAGE=jdk-6u7-linux-$ARCH-rpm.bin
  JDK_INSTALL_PATH=/usr/java/jdk1.6.0_07
else
  JDK_PACKAGE=jdk-6u10-linux-$ARCH-rpm.bin
  JDK_INSTALL_PATH=/usr/java/jdk1.6.0_10
fi
$S3CMD get "cloudera-packages:$JDK_PACKAGE" "$JDK_PACKAGE"

# install Java on the host system so we can run EC2 tools.
chmod 700 "$JDK_PACKAGE"
# java wants to show you a license with more. Disable this so they can't
# interrupt our unattended installation
mv /bin/more /bin/more.no
yes | ./$JDK_PACKAGE -noregister

echo "export JAVA_HOME=$JDK_INSTALL_PATH" >> /etc/profile
export JAVA_HOME=$JDK_INSTALL_PATH
java -version

mv /bin/more.no /bin/more

############# BUILD PACKAGE ####################

# Add centos.karan.org which has lzo-devel and git
curl 'http://centos.karan.org/kbsingh-CentOS-Extras.repo' | sed -e 's/enabled=1/enabled=0/' \
  > /etc/yum.repos.d/kbsingh-Centos-Extras.repo
MD5=$(curl http://centos.karan.org/RPM-GPG-KEY-karan.org.txt | tee /tmp/karan_key | md5sum - | awk '{print $1}')
if [ "$MD5" != "ec11342109b2ab5563265cb75e63df3c" ]; then
  echo MD5 of karan repository key has changed! Now is $MD5
  exit 1
fi

# TODO(todd) we should rebuild lzo-devel and git and put it in our own yum repo so we don't have to
# rely on some random dude

# Satisfy build deps
YUMINST="yum --nogpgcheck --enablerepo=kbs-CentOS-Testing -y install"
$YUMINST git-core
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