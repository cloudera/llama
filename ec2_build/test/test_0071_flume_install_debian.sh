#!/bin/bash -x
# (c) Copyright 2009 Cloudera, Inc.

set -e

############################## SETUP BUILD ENV ##############################

# Install things needed to build
export DEBIAN_FRONTEND=noninteractive

export CDH_REPO_HOST="$1"
export CDH_REPO_VERSION="$2"

# Make sure this script only runs on debian/ubuntu
if [ ! -e "/etc/debian_version" ]; then
  echo "This script is made for debian-like OS. Exiting"
  exit 0
fi

apt-cache search flume flume-master flume-node
apt-get install -y flume flume-master flume-node

echo "Done."
exit 0
