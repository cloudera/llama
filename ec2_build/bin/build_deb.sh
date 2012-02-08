#!/bin/bash
set -ex
export INTERACTIVE=false
export BUILD_USER='jenkins'
export EMAIL_ADDRESS='andrew@cloudera.com'
export CODENAME="${PLATFORM}-${CDH_RELEASE}"
export PYTHONPATH="$WORKSPACE/lib:$PYTHONPATH"

  export OOZIE_SKIP_TEST_EXEC=true
rm -rf ~/.groovy/grapes/com.cloudera.kitchen

  ##SUBSTITUTE_VARS##
  export AWS_ACCESS_KEY_ID
  export AWS_SECRET_ACCESS_KEY

  ############################## SETUP BUILD ENV ##############################


  # Install things needed to build
  export DEBIAN_FRONTEND=noninteractive

  # Clean up ~/.m2/repository, ~/.ivy2, and ~/.ant to make sure we're getting fresh jars.
  # Actually, just nuke CDH stuff.
  rm -rf ~/.m2/repository/org/apache/hadoop*
  rm -rf ~/.m2/repository/org/apache/hbase*
  rm -rf ~/.m2/repository/org/apache/zookeeper*
  rm -rf ~/.m2/repository/org/apache/pig*
  rm -rf ~/.m2/repository/com/cloudera
  rm -rf ~/.m2/repository/com/yahoo/oozie*
  rm -rf ~/.ivy2/cache/org.apache.hadoop
  rm -rf ~/.ivy2/cache/org.apache.zookeeper  
  rm -rf ~/.ivy2/cache/org.apache.pig
  rm -rf ~/.ivy2/cache/org.apache.hbase
  rm -rf ~/.ivy2/cache/com.cloudera*
  rm -rf ~/.ant/cache/org.apache.hadoop
  rm -rf ~/.ant/cache/org.apache.zookeeper  
  rm -rf ~/.ant/cache/org.apache.pig
  rm -rf ~/.ant/cache/org.apache.hbase
  rm -rf ~/.ant/cache/com.cloudera*

  export GROOVY_HOME=/opt/toolchain/groovy-1.8.2
  export PATH=$PATH:$GROOVY_HOME/bin
  
  [ -f /opt/toolchain/toolchain.sh ] && source /opt/toolchain/toolchain.sh
  [ -f /mnt/toolchain/toolchain.sh ] && source /mnt/toolchain/toolchain.sh

  ############################## DOWNLOAD ##############################

  eval `dpkg-architecture` # set DEB_* variables

  for PACKAGE in $PACKAGES; do

    echo "========================"
    echo "Building $PACKAGE"
    echo "========================"

    rm -rf $WORKSPACE/$PARENT_BUILD_ID
    mkdir $WORKSPACE/$PARENT_BUILD_ID
    pushd $WORKSPACE/$PARENT_BUILD_ID

groovy $WORKSPACE/ec2_build/bin/fetchStaging --origin-path=$PARENT_BUILD_ID --file-type="all" --destination="$WORKSPACE/$PARENT_BUILD_ID"

  ############################## BUILD ##############################

    # Unpack source package
    dpkg-source -x *dsc

    pushd `find . -maxdepth 1 -type d | grep -vx .`

#    /usr/lib/pbuilder/pbuilder-satisfydepends
#    apt-get -y remove openjdk* maven2 || /bin/true

    if [ ! -z "$CODENAME" ]; then
      CODENAMETAG="~$CODENAME"
    fi
    VERSION=$(dpkg-parsechangelog | grep '^Version:' | awk '{print $2}')
    NEWVERSION=$VERSION$CODENAMETAG

    DEBEMAIL=info@cloudera.com \
      DEBFULLNAME="Cloudera Automatic Build System" \
      yes | dch --force-bad-version -v $NEWVERSION --distribution $CODENAME "EC2 Build ID $PARENT_BUILD_ID"

    if [ -z "$DEBUILD_FLAG" ]; then
      DEBUILD_FLAG='-b'
    fi

    export IVY_MIRROR_PROP='http://azov01.sf.cloudera.com:8081/artifactory/cloudera-mirrors/'

    debuild --preserve-envvar JAVA_HOME --preserve-envvar JAVA32_HOME --preserve-envvar JAVA64_HOME --preserve-envvar JAVA5_HOME --preserve-envvar MAVEN3_HOME --preserve-envvar THRIFT_HOME --preserve-envvar FORREST_HOME --preserve-envvar IVY_MIRROR_PROP --preserve-envvar NODE_HOME -uc -us $DEBUILD_FLAG
    
    if [ $? -ne 0 ]; then
        echo "***PACKAGE FAILED*** ${PACKAGE} deb_${CODENAME}_${DEB_HOST_ARCH}"
      send_email $PACKAGE $DEB_HOST_ARCH
    else
        echo "***PACKAGE BUILT*** ${PACKAGE} deb_${CODENAME}_${DEB_HOST_ARCH}"
    fi

 #   apt-get -y remove openjdk* maven2 || /bin/true

    popd 

    pwd

    ############################## UPLOAD ##############################

    # we don't want to upload back the source change list
    rm *_source.changes

    FILES=$(grep-dctrl -n -s Files '' *changes | grep . | awk '{print $5}')

#    ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i /root/.ssh/static_vm_key root@${STAGING_HOST} "mkdir -p ${INTERIM_STAGING}/$PARENT_BUILD_ID/binary/deb_${CODENAME}_${DEB_HOST_ARCH}"

#    for f in $FILES *changes ; do
#        scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i /root/.ssh/static_vm_key $f \
#            root@${STAGING_HOST}:${INTERIM_STAGING}/$PARENT_BUILD_ID/binary/deb_${CODENAME}_${DEB_HOST_ARCH}/$(basename $f) 
#        $S3CMD put $S3_BUCKET:build/$PARENT_BUILD_ID/deb_${CODENAME}_${DEB_HOST_ARCH}/$(basename $f) $f x-amz-acl:public-read

#    done

    # Leave /tmp/$PARENT_BUILD_ID

groovy $WORKSPACE/ec2_build/bin/copyToStaging --output-dir=${JOB_NAME}-${BUILD_ID} \
   --file="$WORKSPACE/$PARENT_BUILD_ID/*.deb" --file="$WORKSPACE/$PARENT_BUILD_ID/**/*changes"

groovy $WORKSPACE/ec2_build/bin/genStagingMetadata --build-id "${BUILD_ID}" \
--job-name "${JOB_NAME}" \
--output-dir "${JOB_NAME}-${BUILD_ID}" \
--parent-build-path ${PARENT_BUILD_ID} \
--binary \
--arch ${DEB_HOST_ARCH} \
--platform ${PLATFORM}  \
--package-type deb

  done
