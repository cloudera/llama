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

PACKAGES="hadoop-hbase hadoop-hbase-master hadoop-hbase-regionserver hadoop-hbase-thrift"

yum search $PACKAGES
yum  -y install $PACKAGES
/etc/init.d/haddop-hbase-master restart
/etc/init.d/haddop-hbase-regionserver restart
/etc/init.d/haddop-hbase-thrift restart
yum  -y remove $PACKAGES

yum  -y install $PACKAGES
/etc/init.d/haddop-hbase-master stop
/etc/init.d/haddop-hbase-regionserver stop
/etc/init.d/haddop-hbase-thrift stop
yum  -y remove $PACKAGES

yum  -y install $PACKAGES
/etc/init.d/haddop-hbase-master restart
/etc/init.d/haddop-hbase-regionserver restart
/etc/init.d/haddop-hbase-thrift restart
/etc/init.d/haddop-hbase-master stop
/etc/init.d/haddop-hbase-regionserver stop
/etc/init.d/haddop-hbase-thrift stop
yum  -y remove $PACKAGES
echo "Done."
exit 0
