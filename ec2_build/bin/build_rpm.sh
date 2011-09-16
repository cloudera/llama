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

[ -f /opt/toolchain/toolchain.sh ] && source /opt/toolchain/toolchain.sh
[ -f /mnt/toolchain/toolchain.sh ] && source /mnt/toolchain/toolchain.sh

  # Clean up ~/.m2/repository, ~/.ivy2, and ~/.ant to make sure we're getting fresh jars.
  # Actually, just nuke CDH stuff.
  (cd ~/.m2/repository; find . -name "*cdh*" -type d -exec rm -rf {} \;) || true
  rm -rf ~/.ivy2/org.apache.hadoop
  rm -rf ~/.ivy2/org.apache.zookeeper  
  rm -rf ~/.ivy2/org.apache.pig
  rm -rf ~/.ivy2/org.apache.hbase
  rm -rf ~/.ivy2/com.cloudera*
  rm -rf ~/.ant/cache/org.apache.hadoop
  rm -rf ~/.ant/cache/org.apache.zookeeper  
  rm -rf ~/.ant/cache/org.apache.pig
  rm -rf ~/.ant/cache/org.apache.hbase
  rm -rf ~/.ant/cache/com.cloudera*


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

  for TARGET_ARCH in noarch $ARCH ; do
    rm -Rf $WORKSPACE/buildroot
    mkdir $WORKSPACE/buildroot
    rpmbuild --define "_topdir $WORKSPACE/topdir" --buildroot $WORKSPACE/buildroot --target $TARGET_ARCH --rebuild *src.rpm

    if [ $? -ne 0 ]; then
        echo "***PACKAGE FAILED*** ${PACKAGE} rpm_${CODENAME}_${ARCH}"
    else
        echo "***PACKAGE BUILT*** ${PACKAGE} rpm_${CODENAME}_${ARCH}"
    fi
  done

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
