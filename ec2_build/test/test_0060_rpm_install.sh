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

echo "[cloudera-cdh${CDH_REPO_VERSION}]
name=Cloudera's Distribution for Hadoop, Version ${CDH_REPO_VERSION}
baseurl=http://${CDH_REPO_HOST}/redhat/cdh/${CDH_REPO_VERSION}
gpgkey = http://${CDH_REPO_HOST}/redhat/cdh/RPM-GPG-KEY-cloudera
gpgcheck = 0" > /etc/yum.repos.d/cloudera-cdh${CDH_REPO_VERSION}.repo


yum search hadoop
yum  -y install  hadoop-0.20.noarch hadoop-0.20-conf-pseudo.noarch hadoop-0.20-datanode.noarch hadoop-0.20-docs.noarch hadoop-0.20-jobtracker.noarch hadoop-0.20-libhdfs hadoop-0.20-namenode.noarch hadoop-0.20-native hadoop-0.20-pipes hadoop-0.20-secondarynamenode.noarch hadoop-0.20-tasktracker.noarch

echo "Done."
exit 0
