#!/bin/sh -x
# (c) Copyright 2009 Cloudera, Inc.

set -e

##SUBSTITUTE_VARS##

export AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY

############################## DOWNLOAD ##############################

mkdir /tmp/$BUILD_ID
cd /tmp/$BUILD_ID

# fetch deb parts of manifest
curl -s $MANIFEST_URL | grep ^deb > manifest.txt

# download all the files
perl -n -a -e '
if (/^deb/) {
  print "Fetching $F[1]...\n";
  system("/usr/bin/curl", "-s", "-o", $F[1], $F[3]);
}' manifest.txt


############################## SETUP BUILD ENV ##############################

# Install things needed to build
export DEBIAN_FRONTEND=noninteractive

if [ -z "$(which sudo)" ]; then
    # If we don't have sudo, we're running as root
    # and we just need to update and install it
    aptitude update
    aptitude install sudo
else
    sudo aptitude update
fi

# Some basic tools we need:
#
#  devscripts - debian package building scripts
#  pbuilder - nice util for satisfying build deps
#  debconf-utils - for dch
#  liburi-perl - a dependency of one of the above that isn't resolved automatically
#  build-essential - compilers, etc
#  dctrl-tools - for dpkg-grepctrl

sudo aptitude -y install devscripts pbuilder debconf-utils liburi-perl build-essential \
  dctrl-tools

# Mark java license as accepted so that, if it's pulled in by some package, it won't
# block on user input to accept the sun license (ed: oracle license? haha)
echo 'sun-java6-bin   shared/accepted-sun-dlj-v1-1    boolean true
sun-java6-jdk   shared/accepted-sun-dlj-v1-1    boolean true
sun-java6-jre   shared/accepted-sun-dlj-v1-1    boolean true
sun-java6-jre   sun-java6-jre/stopthread        boolean true
sun-java6-jre   sun-java6-jre/jcepolicy note
sun-java6-bin   shared/present-sun-dlj-v1-1     note
sun-java6-jdk   shared/present-sun-dlj-v1-1     note
sun-java6-jre   shared/present-sun-dlj-v1-1     note
' | sudo debconf-set-selections

# If we're on etch, add backports since we depend on a couple bpo packages
# (eg sun-java6-jdk and debhelper >= 6)
if [ "x$DISTRO" = "xetch" ]; then
  echo 'deb http://www.backports.org/debian etch-backports main contrib non-free' \
  | sudo tee /etc/apt/sources.list.d/backports.list
  sudo aptitude update
  sudo apt-get -t etch-backports -y --force-yes install debhelper
fi

# Install s3cmd
OLDDIR=`pwd`
cd /tmp
  wget http://s3.amazonaws.com/ServEdge_pub/s3sync/s3sync.tar.gz
  tar xzvf s3sync.tar.gz
  S3CMD=`pwd`/s3sync/s3cmd.rb
cd $OLDDIR


############################## BUILD ##############################

# Unpack source package
dpkg-source -x *dsc

cd `find . -maxdepth 1 -type d | grep -vx .`

sudo /usr/lib/pbuilder/pbuilder-satisfydepends

if [ ! -z "$DISTRO" ]; then
  DISTROTAG="~$DISTRO"
fi
VERSION=$(dpkg-parsechangelog | grep Version | awk '{print $2}')
NEWVERSION=$VERSION$DISTROTAG

DEBEMAIL=info@cloudera.com \
  DEBFULLNAME="Cloudera Automatic Build System" \
  yes | dch --force-bad-version -v $NEWVERSION --distribution $DISTRO "EC2 Build ID $BUILD_ID"

if [ -z "$DEBUILD_FLAG" ]; then
  DEBUILD_FLAG='-b'
fi
debuild -uc -us $DEBUILD_FLAG

cd ..




############################## UPLOAD ##############################


eval `dpkg-architecture` # set DEB_* variables
CODENAME=$(lsb_release --short --codename)

# we don't want to upload back the source change list
rm *_source.changes

FILES=$(grep-dctrl -n -s Files '' *changes | grep . | awk '{print $5}')

for f in $FILES *changes ; do
    $S3CMD put $S3_BUCKET:build/$BUILD_ID/deb_${CODENAME}_${DEB_HOST_ARCH}/$(basename $f) $f
done

# If we're running on S3, shutdown the node
# (do the check so you can test elsewhere)
hostname -f | grep -q ec2.internal && shutdown -h now