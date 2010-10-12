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

PACKAGES="hadoop-hbase hadoop-hbase-master hadoop-hbase-regionserver hadoop-hbase-thrift"

apt-cache search $PACKAGES
apt-get install -y $PACKAGES
/etc/init.d/hadoop-hbase-master restart
/etc/init.d/hadoop-hbase-regionserver restart
/etc/init.d/hadoop-hbase-thrift restart
apt-get remove -y $PACKAGES

apt-get install -y $PACKAGES
/etc/init.d/hadoop-hbase-master restart
/etc/init.d/hadoop-hbase-regionserver restart
/etc/init.d/hadoop-hbase-thrift restart
/etc/init.d/hadoop-hbase-master stop
/etc/init.d/hadoop-hbase-regionserver stop
/etc/init.d/hadoop-hbase-thrift stop
apt-get remove -y $PACKAGES

echo "Done."
exit 0
