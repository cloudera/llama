#!/bin/bash -x
# (c) Copyright 2010 Cloudera, Inc.
#
# After a build has been run from ec2_build.py, the build slaves
# have put the resulting debs into an s3 bucket named for the build id.
# This script pulls the rpms out of that bucket and writes them into
# the yum repository using the createrepo tool.

set -ex

# Make sadfjdjsf*fsdasfd expand to "" when there are no matches,
# rather than retaining the literal *
shopt -s nullglob

function usage {
  echo "usage: $0 -s <build directory> -c <cdh release> -r <repo> -p <passphrase> [-d <distro> -u <update>]"
  echo "       build directory: The directory, including build id, where the build output has been copied to."
  echo "       cdh release: The codename for this release (e.g., 2)"
  echo "       repo: The top level dir of the yum repo"
  echo "       passphrase: GNU GPG passphrase to sign rpms (rpm --addsign does not support gpg-agent."
  echo "            It must be provided a passphrase through stdin)"
  echo "       distro: GNU/Linux distribution for which these packages are made (e.g., centos5 or sles11)"
  echo "       archs: The architectures covered for this distro."
  echo "       update: Optional - string to append to the main CDH release."
  exit 1
}

while getopts "s:c:r:p:d:a:" options; do
  case $options in
    s ) BUILD_DIR=$OPTARG;;
    c ) CDH_RELEASE=$OPTARG;;
    r ) REPO=$OPTARG;;
    p ) PASSPHRASE=$OPTARG;;
    d ) DISTRO=$OPTARG;;
    a ) ARCHS=$OPTARG;;
    u ) UPDATE=$OPTARG;;
    h ) usage;;
    \?) usage;;
    * ) usage;;

  esac
done

if [ -z "$BUILD_DIR" ] || [ -z "$CDH_RELEASE" ] || [ -z "$REPO" ]; then
  usage
fi

# This script is called from a lot of places
# So it will default to its old behavior of "centos5" if -d is not provided
if [ -z "$DISTRO" ]; then
DISTRO="centos5"
fi

if [ -z "$ARCHS" ]; then
ARCHS="i386 x86_64"
fi
REALREL=${CDH_RELEASE}${UPDATE}
echo "*** Copying src rpms ***"
mkdir -p $REPO/cdh/$REALREL/SRPMS/
for SRPM in $BUILD_DIR/source/*.src.rpm; do
  echo "Signing $SRPM"
  echo $PASSPHRASE | rpm --addsign $SRPM
  echo "Copying $SRPM  to $REPO/cdh/$REALREL/SRPMS/"
  cp $SRPM $REPO/cdh/$REALREL/SRPMS/
done

mkdir -p $REPO/cdh/$REALREL/RPMS/noarch/
for ARCH in $ARCHS ; do
  mkdir -p $REPO/cdh/$REALREL/RPMS/$ARCH/
  echo "*** Copying $ARCH rpms ***"
  for RPM in $BUILD_DIR/binary/rpm_${DISTRO}-cdh${CDH_RELEASE}_${ARCH}/*.$ARCH.rpm ; do
    echo "Signing $RPM"
    echo $PASSPHRASE | rpm --addsign $RPM
    echo "Copying $RPM to $REPO/cdh/$REALREL/RPMS/$ARCH/"
    cp $RPM $REPO/cdh/$REALREL/RPMS/$ARCH/
  done
  echo "*** Copying noarch rpms ***"
  for RPM in $BUILD_DIR/binary/rpm_${DISTRO}-cdh${CDH_RELEASE}_${ARCH}/*.noarch.rpm ; do
    echo "Signing $RPM"
    echo $PASSPHRASE | rpm --addsign $RPM
    echo "Copying $RPM to $REPO/cdh/$REALREL/RPMS/noarch/"
    cp $RPM $REPO/cdh/$REALREL/RPMS/noarch/
  done
done

createrepo $REPO/cdh/$REALREL

pushd $REPO/cdh/$REALREL/repodata

if [ "$DISTRO" = "sles11" ]; then
    cp $REPO/cdh/RPM-GPG-KEY-cloudera repomd.xml.key
fi

for FILE in ./*.asc; do
    rm $FILE
done

for FILE in ./*; do
  echo "Signing $FILE"
  gpg --armor --detach-sign --batch --passphrase $PASSPHRASE $FILE
done

popd
