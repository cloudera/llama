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

yum search flume flume-master flume-node
yum  -y install flume flume-master flume-node
/etc/init.d/flume-master restart
/etc/init.d/flume-node restart
yum  -y remove flume flume-master flume-node

yum  -y install flume flume-master flume-node
/etc/init.d/flume-master restart
/etc/init.d/flume-node restart
/etc/init.d/flume-master stop
/etc/init.d/flume-node stop
yum  -y remove flume flume-master flume-node

echo "Done."
exit 0
