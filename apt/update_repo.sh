#!/bin/bash -x
# (c) Copyright 2009 Cloudera, Inc.
#
# After a build has been run from ec2_build.py, the build slaves
# have put the resulting debs into an s3 bucket named for the build id.
# This script pulls the debs out of that bucket and writes them into
# the APT repository using the reprepro tool.

set -e

# Make sadfjdjsf*fsdasfd expand to "" when there are no matches,
# rather than retaining the literal *
shopt -s nullglob

function usage {
  echo "usage: $0 -b <build_id> -c <cdh release> -r <repo> -d <base dir> [-u <update>]"
  echo "       build_id: The dir in the base dir with the debs (e.g., chad-20090810_192726)"
  echo "       cdh release: The codename for this release (e.g., cdh2)"
  echo "       repo: The top level dir of the apt repo"
  echo "       base dir: Base directory where everything's been copied to."
  echo "       update: Optional - string to append to the main CDH release."
  exit 1
}

while getopts "b:c:r:d:u:" options; do
  case $options in
    b ) BUILD_ID=$OPTARG;;
    c ) CDH_RELEASE=$OPTARG;;
    r ) REPO=$OPTARG;;
    d ) BASE_DIR=$OPTARG;;
    u ) UPDATE=$OPTARG;;
    h ) usage;;
    \?) usage;;
    * ) usage;;

  esac
done

if [ -z "$BASE_DIR" ] || [ -z "$BUILD_ID" ] || [ -z "$CDH_RELEASE" ] || [ -z "$REPO" ]; then
  usage
fi

# Sanity check:
#
# DEBIAN_DISTROS: lenny, hardy, intrepid, jaunty, etc
# DEBIAN_SUITES: stable, testing
# SUITE: $DEBIAN_DISTRO-$DEBIAN_SUITE, $DEBIAN_DISTRO-testing 
# CDH_RELEASE: cdh1, cdh2
# CODENAME: $DEBIAN_DISTRO-$CDH_RELEASE
# RELEASE: a build with version info hadoop-0.20_0.20.0+69+desktop.49-1cdh~intrepid-cdh2_i386

DEBIAN_DISTROS="lenny lucid maverick squeeze"

ARCHS="i386 amd64"

REALREL=${CDH_RELEASE}${UPDATE}

REPREPRO_FLAGS="--export=never --keepunreferenced --ignore=wrongdistribution --basedir $REPO"

for DEBIAN_DISTRO in $DEBIAN_DISTROS ; do

  CODENAME=$DEBIAN_DISTRO-$REALREL

  # include source package
  for changefile in $BASE_DIR/$BUILD_ID/source/*source.changes ; do
      reprepro --ignore=wrongdistribution $REPREPRO_FLAGS include $CODENAME $changefile
  done
  for ARCH in $ARCHS ; do
    BUILD_DIR=$BUILD_ID/deb_${CODENAME}_${ARCH}
    if [ $ARCH = "i386" ]; then
      # On i386, install all built packages
      for changefile in $BASE_DIR/$BUILD_ID/*changes ; do
        reprepro $REPREPRO_FLAGS include $CODENAME $changefile
      done

      #include all packages
      for deb in $BASE_DIR/$BUILD_ID/binary/deb_${DEBIAN_DISTRO}-${CDH_RELEASE}_${ARCH}/*${DEBIAN_DISTRO}-${CDH_RELEASE}_all.deb ; do
        reprepro $REPREPRO_FLAGS includedeb $CODENAME $deb
      done
    fi

    #include arch specific packages
    for deb in $BASE_DIR/$BUILD_ID/binary/deb_${DEBIAN_DISTRO}-${CDH_RELEASE}_${ARCH}/*${DEBIAN_DISTRO}-${CDH_RELEASE}_${ARCH}.deb ; do
      reprepro $REPREPRO_FLAGS includedeb $CODENAME $deb
    done
  done
done

echo
echo Exporting repo and running checks...
echo

for action in export check checkpool ; do
  echo $action...
  reprepro --basedir $REPO $action
  echo done
done

echo createsymlinks...
reprepro --delete --basedir $REPO createsymlinks
echo done
