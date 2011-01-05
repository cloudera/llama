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

PACKAGES="oozie oozie-client"

yum search $PACKAGES
yum  -y install $PACKAGES
yum  -y remove $PACKAGES

echo "Done."
exit 0
