#!/bin/bash
set -ex
export INTERACTIVE=false
export BUILD_USER='jenkins'
export EMAIL_ADDRESS='andrew@cloudera.com'
export CODENAME="${PLATFORM}-${CDH_RELEASE}"
export PYTHONPATH="$WORKSPACE/lib:$PYTHONPATH"
export OOZIE_SKIP_TEST_EXEC=true
export ARCH=$(uname -i)

export GROOVY_HOME=/opt/toolchain/groovy-1.8.2
export PATH=$PATH:$GROOVY_HOME/bin
rm -rf ~/.groovy/grapes/com.cloudera.kitchen
rm -rf /tmp/forrest-jenkins

[ -f /opt/toolchain/toolchain.sh ] && source /opt/toolchain/toolchain.sh
[ -f /mnt/toolchain/toolchain.sh ] && source /mnt/toolchain/toolchain.sh

  # Clean up ~/.m2/repository, ~/.ivy2, and ~/.ant to make sure we're getting fresh jars.
  # Actually, just nuke CDH stuff.
  rm -rf ~/.m2/repository/org/apache/solr*
  rm -rf ~/.m2/repository/org/apache/lucene*
  rm -rf ~/.m2/repository/org/apache/hadoop*
  rm -rf ~/.m2/repository/org/apache/hbase*
  rm -rf ~/.m2/repository/org/apache/hive*
  rm -rf ~/.m2/repository/org/apache/zookeeper*
  rm -rf ~/.m2/repository/org/apache/pig*
  rm -rf ~/.m2/repository/com/cloudera
  rm -rf ~/.m2/repository/com/yahoo/oozie*
  rm -rf ~/.m2/repository/org/slf4j
  rm -rf ~/.ivy2/cache/org.apache.hadoop
  rm -rf ~/.ivy2/cache/org.apache.zookeeper  
  rm -rf ~/.ivy2/cache/org.apache.pig
  rm -rf ~/.ivy2/cache/org.apache.hbase
  rm -rf ~/.ivy2/cache/org.apache.hive
  rm -rf ~/.ivy2/cache/com.cloudera*
  rm -rf ~/.ant/cache/org.apache.hadoop
  rm -rf ~/.ant/cache/org.apache.zookeeper  
  rm -rf ~/.ant/cache/org.apache.pig
  rm -rf ~/.ant/cache/org.apache.hbase
  rm -rf ~/.ant/cache/org.apache.hive
  rm -rf ~/.ant/cache/com.cloudera*
  rm -rf ~/.ivy2/local


# Put %dist into ~/.rpmmacros if needed
if [ ! -f ~/.rpmmacros ] || ! grep -q '%dist' ~/.rpmmacros; then
    if [ "${PLATFORM}" == "rhel6" ]; then
        echo '%dist   .el6' >> ~/.rpmmacros
    elif [ "${PLATFORM}" == "centos5" ]; then
        echo '%dist   .el5' >> ~/.rpmmacros
    elif [ "${PLATFORM}" == "sles11" ]; then
        echo '%dist   .sles11' >> ~/.rpmmacros
    fi
fi

############# BUILD PACKAGE ####################

for PACKAGE in $PACKAGES; do

  echo "========================"
  echo "Building $PACKAGE"
  echo "========================"
  rm -rf ${WORKSPACE}/$PARENT_BUILD_ID
  mkdir ${WORKSPACE}/$PARENT_BUILD_ID
  pushd ${WORKSPACE}/$PARENT_BUILD_ID
  # get the files
  groovy $WORKSPACE/ec2_build/bin/fetchStaging --origin-path=$PARENT_BUILD_ID --file-type="all" --destination="$WORKSPACE/$PARENT_BUILD_ID"
  export IVY_MIRROR_PROP='http://azov01.sf.cloudera.com:8081/artifactory/cloudera-mirrors/'

  # make build dir
  rm -Rf $WORKSPACE/topdir
  mkdir $WORKSPACE/topdir
  (cd $WORKSPACE/topdir && mkdir BUILD RPMS SOURCE SPECS SRPMS)

  rm -Rf $WORKSPACE/buildroot
  mkdir $WORKSPACE/buildroot
  rpmbuild --define "_topdir $WORKSPACE/topdir" --buildroot $WORKSPACE/buildroot --rebuild *src.rpm

  if [ $? -ne 0 ]; then
      echo "***PACKAGE FAILED*** ${PACKAGE} rpm_${CODENAME}_${ARCH}"
  else
      echo "***PACKAGE BUILT*** ${PACKAGE} rpm_${CODENAME}_${ARCH}"
  fi


  ############################## UPLOAD ##############################

  for arch_dir in ${WORKSPACE}/topdir/RPMS/* ; do
    groovy $WORKSPACE/ec2_build/bin/copyToStaging --output-dir=${JOB_NAME}-${BUILD_ID} \
      --file="${arch_dir}/*.rpm"
    groovy $WORKSPACE/ec2_build/bin/genStagingMetadata --build-id "${BUILD_ID}" \
        --job-name "${JOB_NAME}" \
        --output-dir "${JOB_NAME}-${BUILD_ID}" \
        --parent-build-path ${PARENT_BUILD_ID} \
        --binary \
        --arch ${ARCH} \
        --platform ${PLATFORM} \
        --package-type rpm

  done

done
