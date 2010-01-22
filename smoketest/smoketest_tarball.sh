#!/bin/bash

if [ $# != 1 ]; then
  echo Usage: $0 hadoop.tar.gz 1>&2
  exit 1
fi

HADOOP_DAEMON_PIDS=""

failure() {
  status=$?
  if [ ! -z "$HADOOP_DAEMON_PIDS" ]; then
    echo Killing hadoop daemons. pids: $HADOOP_DAEMON_PIDS
    kill -9 $HADOOP_DAEMON_PIDS
  fi
  if [ $status -ne 0 ]; then
    (echo
     echo 
     echo =======================
     echo Tests failed!
     echo See above log for info about what failed
     echo =======================) 1>&2
    exit $status
  fi
}

start_daemon() {
  echo starting daemon $1
  ./bin/hadoop $1 &
  pid=$!
  HADOOP_DAEMON_PIDS="$HADOOP_DAEMON_PIDS $pid"
  LAST_STARTED=$pid
}

set -e
trap failure EXIT
TARNAME=$(basename $1)
HADOOP=${TARNAME%.tar.gz}

TMP=`mktemp -d`
cp $1 $TMP/

echo Unpacking tarball in $TMP

cd $TMP
tar xzf $TARNAME
cd $HADOOP

# All the tests
FILES=(
  cloudera/CHANGES.cloudera.txt 
  lib/native/Linux-i386-32/libhadoop.so.1.0.0
  lib/native/Linux-i386-32/libhadoop.so
  lib/native/Linux-i386-32/libhadoop.so.1
  lib/native/Linux-i386-32/libhadoop.a
  lib/native/Linux-i386-32/libhadoop.la
  lib/native/Linux-amd64-64/libhadoop.so.1.0.0
  lib/native/Linux-amd64-64/libhadoop.so
  lib/native/Linux-amd64-64/libhadoop.so.1
  lib/native/Linux-amd64-64/libhadoop.a
  lib/native/Linux-amd64-64/libhadoop.la
  lib/$HADOOP-fairscheduler.jar
  docs/index.html
  c++/Linux-amd64-64/lib/libhadooputils.a
  c++/Linux-amd64-64/lib/libhdfs.so
  c++/Linux-amd64-64/lib/libhadooppipes.a
  c++/Linux-amd64-64/lib/libhdfs.so.0
  c++/Linux-amd64-64/lib/libhdfs.la
  c++/Linux-amd64-64/lib/libhdfs.so.0.0.0
  c++/Linux-i386-32/lib/libhadooputils.a
  c++/Linux-i386-32/lib/libhdfs.so
  c++/Linux-i386-32/lib/libhadooppipes.a
  c++/Linux-i386-32/lib/libhdfs.so.0
  c++/Linux-i386-32/lib/libhdfs.la
  c++/Linux-i386-32/lib/libhdfs.so.0.0.0
  example-confs/conf.pseudo/README
  bin/sqoop
  lib/$HADOOP-scribe-log4j.jar
  lib/libthrift.jar
  lib/libfb303.jar
)

echo Checking files
echo ============================

for file in ${FILES[*]} ; do
  echo Checking for $file...
  test -e $file
done

echo Checking version script...

./bin/hadoop version | (
  read hadoop version
  if [ $version != ${HADOOP#hadoop-} ]; then
    echo Version $version doesnt match $HADOOP
    exit 1
  fi

  read x x githash
  if ! [[ $githash =~ [a-z0-9]{40} ]]; then
    echo Bad git hash: $githash
    exit 1
  fi
)
test $? -eq 0


echo Checking local runner pi job...

./bin/hadoop jar hadoop-*-examples.jar pi 1 1000

echo Checking local fs

./bin/hadoop fs -ls . > /dev/null

echo Checking pseudodistributed
cp -a example-confs/conf.pseudo conf.test

mkdir -p $TMP/root

for x in conf.test/* ; do
  sed -i -e "s,/var,$TMP/root," $x
done

export HADOOP_CONF_DIR=conf.test
bin/hadoop namenode -format

start_daemon namenode
start_daemon datanode
./bin/hadoop dfsadmin -safemode wait
sleep 60
start_daemon jobtracker
JT=$LAST_STARTED
start_daemon tasktracker
TT=$LAST_STARTED

./bin/hadoop fs -ls /
./bin/hadoop fs -copyFromLocal /etc/motd /motd
HD_MD5=$(./bin/hadoop fs -cat /motd | md5sum -)
if [ "$HD_MD5" != "$(cat /etc/motd | md5sum -)" ]; then
  echo Md5s did not match!
  exit 1
fi
./bin/hadoop jar hadoop-*-examples.jar pi 1 1000

kill $JT $TT

######## FAIR SCHEDULER TIME, BABY! ############
perl -p -i -e "s,</configuration>,
<property>
  <name>mapred.jobtracker.taskScheduler</name>
  <value>org.apache.hadoop.mapred.FairScheduler</value>
</property>
</configuration>,
" $HADOOP_CONF_DIR/mapred-site.xml

start_daemon jobtracker
start_daemon tasktracker

./bin/hadoop jar hadoop-*-examples.jar pi 1 1000

