#!/bin/sh -x
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

if [ $# -ne 2 ]; then
  echo usage: $0 build_bucket build_id
  exit 1
fi

S3_BUCKET=$1
BUILD_ID=$2
DISTROS="etch lenny hardy intrepid"
ARCHS="i386 amd64"
REPO=repo

# Download all the build data
s3cmd sync s3://$S3_BUCKET/build/$BUILD_ID $BUILD_ID

# Look at the manifest and fetch the source package
cd $BUILD_ID
mkdir -p source
perl -n -a -e '
if (/^deb/) {
  print "Fetching $F[1]...\n";
  system("/usr/bin/curl", "-s", "-o", "source/" . $F[1], $F[3]);
}' manifest.txt
cd ..


REPREPRO_FLAGS="--export=never --keepunreferenced --basedir $REPO"

for DISTRO in $DISTROS ; do

  # include source package
  for changefile in $BUILD_ID/source/*changes ; do
      reprepro --ignore=wrongdistribution $REPREPRO_FLAGS include $DISTRO $changefile
  done

  # Include binary packages
  for ARCH in $ARCHS ; do
    BUILD_DIR=$BUILD_ID/deb_${DISTRO}_${ARCH}
    if [ $ARCH = "i386" ]; then
      # On i386, install all built packages
      for changefile in $BUILD_DIR/*changes ; do
        reprepro $REPREPRO_FLAGS include $DISTRO $changefile
      done
    else
      # On other platforms, however, include just the arch-specific, since
      # otherwise we'll get a hash-conflict, since we already included the
      # _all.deb packages from i386
      for deb in $BUILD_DIR/*_$ARCH.deb ; do
        reprepro $REPREPRO_FLAGS includedeb $DISTRO $deb
      done
    fi
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