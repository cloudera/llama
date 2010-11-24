#!/bin/bash -x
# (c) Copyright 2010 Cloudera, Inc.
#
# After a build has been run from ec2_build.py, the build slaves
# have put the resulting debs into an s3 bucket named for the build id.
# This script pulls the debs out of that bucket and writes them into
# the APT repository using the reprepro tool.

set -ex

# Make sadfjdjsf*fsdasfd expand to "" when there are no matches,
# rather than retaining the literal *
shopt -s nullglob

function usage {
  echo "usage: $0 -s <s3_bucket> -b <build_id> -c <cdh release> -r <repo>"
  echo "       s3_bucket: The S3 bucket the debs are in (e.g., ec2-build)"
  echo "       build_id: The dir in the s3 bucket with the debs (e.g., chad-20090810_192726)"
  echo "       cdh release: The codename for this release (e.g., cdh2)"
  echo "       repo: The top level dir of the apt repo"
  exit 1
}

while getopts "s:b:c:r:" options; do
  case $options in
    b ) BUILD_ID=$OPTARG;;
    c ) CDH_RELEASE=$OPTARG;;
    r ) REPO=$OPTARG;;
    h ) usage;;
    \?) usage;;
    * ) usage;;

  esac
done

if [ -z "$BUILD_ID" ] || [ -z "$CDH_RELEASE" ] || [ -z "$REPO" ]; then
  usage
fi

BASE_DIR="/tmp"
DESTINATION_DIR="$REPO/cdh/$CDH_RELEASE"

ARCHIVES=`find $BASE_DIR/$BUILD_ID/source/  -regextype posix-extended   -regex '.*[[:digit:]]+\.tar\.gz'`

for ARCHIVE in $ARCHIVES; do
  ARCHIVE_NAME=`echo "$ARCHIVE" | sed -e 's/.tar.gz//'`
  ARCHIVE_NAME=`echo "$ARCHIVE_NAME" | sed -e 's/.*\///'`

  PACKAGE_NAME=`echo "$ARCHIVE_NAME" | sed -e 's/-.*//'`

  # Copy archive file
  cp $ARCHIVE $DESTINATION_DIR/

  # Clean up location where archive is going to be uncompressed
  rm -rf $BASE_DIR/$ARCHIVE_NAME
  tar -xzf $ARCHIVE -C $BASE_DIR

  # Extract Change file
  CHANGE_FILE=$BASE_DIR/$ARCHIVE_NAME/CHANGES.txt
  if [ -x $CHANGE_FILE ]; then
    cp $CHANGE_FILE  $DESTINATION_DIR/$ARCHIVE_NAME.CHANGES.txt
  fi

  # Create directory for docs
  mkdir -p  $DESTINATION_DIR/$ARCHIVE_NAME/

  # Copy docs directory
  cp -r $BASE_DIR/$ARCHIVE_NAME/docs/*  $DESTINATION_DIR/$ARCHIVE_NAME/

  # Remove general symlink to the project doc
  rm $DESTINATION_DIR/$PACKAGE_NAME

  # Create a new symlink to the new documentation
  pushd $DESTINATION_DIR
    ln -s $ARCHIVE_NAME $PACKAGE_NAME
  popd

done

echo done
