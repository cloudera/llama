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

yum search hadoop-hive-webinterface hadoop-hive
yum -y install hadoop-hive-webinterface hadoop-hive
/etc/init.d/hive-hwi restart
yum -y remove hadoop-hive-webinterface hadoop-hive

yum -y install hadoop-hive-webinterface hadoop-hive
/etc/init.d/hive-hwi restart
/etc/init.d/hive-hwi stop
yum -y remove hadoop-hive-webinterface hadoop-hive

echo "Done."
exit 0
