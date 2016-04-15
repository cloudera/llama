#!/bin/bash
set -ex

. /opt/toolchain/toolchain.sh

export JAVA_HOME=$JAVA7_HOME
export PATH=${JAVA_HOME}/bin:${PATH}

export THRIFT_HOME=/opt/toolchain/thrift-0.9.0
export PATH=${THRIFT_HOME}/bin:${PATH}

export THRIFT_VERSION=`thrift --version`
if [ "${THRIFT_VERSION}" != "Thrift version 0.9.0" ];
then
  echo "Incorrect ${THRIFT_VERSION}, it should be 0.9.0"
  exit 1
fi

rm -rf ~/.m2/repository

mvn clean package -Pdist -Dtar -Dmaven.javadoc.skip=true -DskipTests
mvn test
