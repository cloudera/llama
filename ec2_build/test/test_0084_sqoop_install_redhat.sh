#!/bin/bash -x
# (c) Copyright 2009 Cloudera, Inc.
set -e

export CDH_REPO_HOST="$1"
export CDH_REPO_VERSION="$2"

# Make sure this script only runs on redhat
if [ ! -e "/etc/redhat-release" ]; then
  echo "This script is made for redhat-like OS. Exiting"
  exit 0
fi

yum search sqoop
yum  -y install sqoop sqoop-metastore
sqoop-version
yum  -y remove sqoop sqoop-metastore

echo "Done."
exit 0
