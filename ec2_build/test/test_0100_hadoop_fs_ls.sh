#!/bin/bash -x
# (c) Copyright 2009 Cloudera, Inc.
set -e

/etc/init.d/hadoop-0.20-namenode restart
/etc/init.d/hadoop-0.20-datanode restart

hadoop fs -ls /

exit $?
