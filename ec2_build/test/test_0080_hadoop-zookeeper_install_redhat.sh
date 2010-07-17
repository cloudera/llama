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

yum search hadoop-zookeeper hadoop-zookeeper-server
yum  -y install hadoop-zookeeper hadoop-zookeeper-server
/etc/init.d/hadoop-zookeeper restart
yum  -y remove hadoop-zookeeper hadoop-zookeeper-server

yum  -y install hadoop-zookeeper hadoop-zookeeper-server
/etc/init.d/hadoop-zookeeper restart
/etc/init.d/hadoop-zookeeper stop
yum  -y remove hadoop-zookeeper hadoop-zookeeper-server

echo "Done."
exit 0
