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
  echo "usage: $0 -s <s3_bucket_directory> -b <build_id> -c <cdh release> -r <repo>"
  echo "       s3_bucket_directory: The S3 bucket path where the rpms are in (e.g., /tmp/cdh2u1rc5)"
  echo "       build_id: The dir in the s3 bucket with the debs (e.g., chad-20090810_192726)"
  echo "       cdh release: The codename for this release (e.g., 2)"
  echo "       repo: The top level dir of the yum repo"
  exit 1
}

while getopts "s:b:c:r:" options; do
  case $options in
    s ) S3_BUCKET=$OPTARG;;
    c ) CDH_RELEASE=$OPTARG;;
    r ) REPO=$OPTARG;;
    h ) usage;;
    \?) usage;;
    * ) usage;;

  esac
done

if [ -z "$S3_BUCKET" ] || [ -z "$CDH_RELEASE" ] || [ -z "$REPO" ]; then
  usage
fi

ARCHS="i386 x86_64"

echo "*** Copying src rpms ***"
for SRPM in $S3_BUCKET/source/*.src.rpm; do
  echo "Copying $SRPM  to $REPO/cdh/$CDH_RELEASE/SRPMS/"
  cp $SRPM $REPO/cdh/$CDH_RELEASE/SRPMS/
done

mkdir -p $RPM $REPO/cdh/$CDH_RELEASE/RPMS/noarch/
for ARCH in $ARCHS ; do
  mkdir -p $REPO/cdh/$CDH_RELEASE/RPMS/$ARCH/
  echo "*** Copying $ARCH rpms ***"
  for RPM in $S3_BUCKET/binary/rpm_centos5-cdh${CDH_RELEASE}_${ARCH}/*.$ARCH.rpm ; do
    echo "Copying $RPM to $REPO/cdh/$CDH_RELEASE/RPMS/$ARCH/"
    cp $RPM $REPO/cdh/$CDH_RELEASE/RPMS/$ARCH/
  done
  echo "*** Copying noarch rpms ***"
  for RPM in $S3_BUCKET/binary/rpm_centos5-cdh${CDH_RELEASE}_${ARCH}/*.noarch.rpm ; do
    echo "Copying $RPM to $REPO/cdh/$CDH_RELEASE/RPMS/noarch/"
    cp $RPM $REPO/cdh/$CDH_RELEASE/RPMS/noarch/
  done
done

createrepo $REPO/cdh/$CDH_RELEASE
