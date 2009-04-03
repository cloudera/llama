#!/bin/sh
# Copyright 2009 Cloudera, inc.

set -e

# Redhat seems to prefer runuser, debian su
if [ -x /sbin/runuser ]; then
  SU_COMMAND=/sbin/runuser
else
  SU_COMMAND=su
fi

if [ -x /usr/sbin/update-alternatives ]; then
  ALTERNATIVES_COMMAND=/usr/sbin/update-alternatives
else
  ALTERNATIVES_COMMAND=/usr/sbin/alternatives
fi

hadoop_username=hadoop


# Install config alternatives
$ALTERNATIVES_COMMAND --install /etc/hive/conf hive-conf /etc/hive/conf.dist 30

# Set up directories on HDFS
$SU_COMMAND -s /bin/bash - ${hadoop_username} -c 'hadoop fs -mkdir       /tmp' 1>/dev/null 2>/dev/null || :
$SU_COMMAND -s /bin/bash - ${hadoop_username} -c 'hadoop fs -mkdir       /user/hive/warehouse' 1>/dev/null 2>/dev/null || :
$SU_COMMAND -s /bin/bash - ${hadoop_username} -c 'hadoop fs -chmod g+w   /tmp' 1>/dev/null 2>/dev/null || :
$SU_COMMAND -s /bin/bash - ${hadoop_username} -c 'hadoop fs -chmod g+w   /user/hive/warehouse' 1>/dev/null 2>/dev/null || :

# Ensure sticky bit on metastore dir - debian likes this to be done in postinst rather than the package
chmod 1777 /var/lib/hive/metastore
