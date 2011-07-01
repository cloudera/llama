#!/bin/bash -x
# (c) Copyright 2009 Cloudera, Inc.

  function copy_logs_s3 {
      ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i /root/.ssh/static_vm_key root@${STAGING_HOST} "mkdir -p ${INTERIM_STAGING}/$BUILD_ID/binary/deb_${CODENAME}_${DEB_HOST_ARCH}"
      scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i /root/.ssh/static_vm_key /var/log/user.log root@${STAGING_HOST}:${INTERIM_STAGING}/$BUILD_ID/deb_${CODENAME}_${DEB_HOST_ARCH} 
  }

  function send_email {
    PACKAGE=$1
    TARGET_ARCH=$2

    TMPFILE=$(mktemp)
    TIME=$(date)
    HOST=$(hostname)
    cat > $TMPFILE << EOF
From: build@cloudera.com
To: $EMAIL_ADDRESS
Subject: $BUILD_ID $PACKAGE debs failed for $TARGET_ARCH

The failure happened at $TIME on $HOST.

Check $S3_BUCKET:build/$BUILD_ID/deb_${CODENAME}_${DEB_HOST_ARCH}/user.log for details

EOF

    cat $TMPFILE | sendmail -t
    rm -f $TMPFILE
  }


  if [ "x$INTERACTIVE" == "xFalse" ]; then
    trap "copy_logs_s3; hostname -f | grep -q internal && shutdown -h now;" INT TERM EXIT
  fi

{
  export OOZIE_SKIP_TEST_EXEC=true

  ##SUBSTITUTE_VARS##
  export AWS_ACCESS_KEY_ID
  export AWS_SECRET_ACCESS_KEY

  ############################## SETUP BUILD ENV ##############################


  # Install things needed to build
  export DEBIAN_FRONTEND=noninteractive

  # The Karmic AMI is from Canonical instead of Alestic. They don't enable multiverse by
  # default. We need multiverse for sun-java6-jdk.
  if [ $(lsb_release -c -s) == "karmic" ]; then
    if ! grep '^deb ' /etc/apt/sources.list | grep -q multiverse; then
      cat >> /etc/apt/sources.list <<'EOF'
deb http://us.archive.ubuntu.com/ubuntu/ karmic multiverse
deb http://us.archive.ubuntu.com/ubuntu/ karmic-updates multiverse
deb http://security.ubuntu.com/ubuntu/ karmic-security multiverse
EOF
    fi
  fi

  # Canoncial moved Sun Java into the partner repo. Pull in the multiverse just in case.
  if [ $(lsb_release -c -s) == "lucid" ]; then
    if ! grep '^deb ' /etc/apt/sources.list | grep -q multiverse; then
      cat >> /etc/apt/sources.list <<'EOF'
deb http://us.archive.ubuntu.com/ubuntu/ lucid multiverse
deb http://us.archive.ubuntu.com/ubuntu/ lucid-updates multiverse
deb http://security.ubuntu.com/ubuntu/ lucid-security multiverse
EOF
    fi
    if ! grep '^deb ' /etc/apt/sources.list | grep -q partner; then
      echo 'deb http://archive.canonical.com/ lucid partner' >> /etc/apt/sources.list
    fi
  fi
  if [ $(lsb_release -c -s) == "maverick" ]; then
    if ! grep '^deb ' /etc/apt/sources.list | grep -q multiverse; then
      cat >> /etc/apt/sources.list <<'EOF'
deb http://us.archive.ubuntu.com/ubuntu/ maverick multiverse
deb http://us.archive.ubuntu.com/ubuntu/ maverick-updates multiverse
deb http://security.ubuntu.com/ubuntu/ maverick-security multiverse
EOF
    fi
    if ! grep '^deb ' /etc/apt/sources.list | grep -q partner; then
      echo 'deb http://archive.canonical.com/ maverick partner' >> /etc/apt/sources.list
    fi
  fi

  if [ $(lsb_release -c -s) == "squeeze" ]; then
    SQUEEZE_NON_FREE_REPO="/etc/apt/sources.list.d/squeeze-non-free.list"
    echo 'deb http://ftp.debian.org/debian/ squeeze non-free' > $SQUEEZE_NON_FREE_REPO
    echo 'deb-src http://ftp.debian.org/debian/ squeeze non-free' >> $SQUEEZE_NON_FREE_REPO
  fi

  apt-get update

  # Some basic tools we need:
  #
  #  devscripts - debian package building scripts
  #  pbuilder - nice util for satisfying build deps
  #  debconf-utils - for dch
  #  liburi-perl - a dependency of one of the above that isn't resolved automatically
  #  build-essential - compilers, etc
  #  dctrl-tools - for dpkg-grepctrl

  # Need to do this first so we can set the debconfs before other packages (e.g., postfix)
  # get pulled in
  apt-get update
  apt-get -y install debconf-utils 
  apt-get -y install libssl-dev

  # Mark java license as accepted so that, if it's pulled in by some package, it won't
  # block on user input to accept the sun license (ed: oracle license? haha)
  echo 'sun-java6-bin   shared/accepted-sun-dlj-v1-1    boolean true
  sun-java6-jdk   shared/accepted-sun-dlj-v1-1    boolean true
  sun-java6-jre   shared/accepted-sun-dlj-v1-1    boolean true
  sun-java6-jre   sun-java6-jre/stopthread        boolean true
  sun-java6-jre   sun-java6-jre/jcepolicy note
  sun-java6-bin   shared/present-sun-dlj-v1-1     note
  sun-java6-jdk   shared/present-sun-dlj-v1-1     note
  sun-java6-jre   shared/present-sun-dlj-v1-1     note
  postfix  postfix/main_mailer_type  select  Local only
  postfix  postfix/root_address  string 
  postfix  postfix/rfc1035_violation  boolean  false
  postfix  postfix/retry_upgrade_warning boolean
  # Install postfix despite an unsupported kernel?
  postfix  postfix/kernel_version_warning  boolean
  postfix  postfix/mydomain_warning  boolean 
  postfix  postfix/mynetworks  string  127.0.0.0/8 [::ffff:127.0.0.0]/104 [::1]/128
  postfix  postfix/not_configured  error 
  postfix  postfix/mailbox_limit string 0
  postfix  postfix/relayhost string  
  postfix  postfix/procmail  boolean false
  postfix  postfix/bad_recipient_delimiter error 
  postfix  postfix/protocols select  all
  postfix  postfix/mailname  string  dontcare
  postfix  postfix/tlsmgr_upgrade_warning  boolean 
  postfix  postfix/recipient_delim  string +
  postfix  postfix/main_mailer_type  select  Local only
  postfix  postfix/destinations  string  localhost
  postfix  postfix/chattr  boolean  false
  ' | debconf-set-selections

  apt-get -y install devscripts pbuilder liburi-perl build-essential dctrl-tools 
  apt-get -y install asciidoc xmlto ruby libopenssl-ruby 
  apt-get -y install fuse-utils autoconf automake libfuse-dev libfuse2
  apt-get -y install subversion sun-java6-jdk
  apt-get -y install libboost-dev libevent-dev python-dev pkg-config libtool flex bison
  apt-get -y install sendmail

  pushd /tmp
    wget http://archive.apache.org/dist/maven/binaries/apache-maven-3.0.2-bin.tar.gz
    tar zxvf apache-maven-*tar.gz
    ln -s /tmp/apache-maven-3.0.2/bin/mvn /usr/bin/mvn
popd

  # install thrift
  pushd /tmp
    wget http://www.ibiblio.org/pub/mirrors/apache/incubator/thrift/0.2.0-incubating/thrift-0.2.0-incubating.tar.gz 
    tar zxvf thrift-0.2.0-incubating.tar.gz
    pushd thrift-0.2.0
      ./configure --without-ruby
      make
      make install
    popd
  popd

  # Install s3cmd
  pushd /tmp
    wget http://s3.amazonaws.com/ServEdge_pub/s3sync/s3sync.tar.gz
    tar xzvf s3sync.tar.gz
    export S3CMD=`pwd`/s3sync/s3cmd.rb
  popd

  [ -f /opt/toolchain/toolchain.sh ] && source /opt/toolchain/toolchain.sh
  [ -f /mnt/toolchain/toolchain.sh ] && source /mnt/toolchain/toolchain.sh

  ############################## DOWNLOAD ##############################

  eval `dpkg-architecture` # set DEB_* variables

  for PACKAGE in $PACKAGES; do

    echo "========================"
    echo "Building $PACKAGE"
    echo "========================"

    mkdir /tmp/$BUILD_ID
    pushd /tmp/$BUILD_ID

    # fetch deb parts of manifest
    curl -s $MANIFEST_URL | grep ^$PACKAGE > manifest.txt

    # download all the files
    perl -n -a -e "
    if (/^$PACKAGE-deb/) {
      print \"Fetching \$F[1]...\\n\";
      system(\"/usr/bin/curl\", \"-s\", \"-o\", \$F[1], \$F[3]);
    }" manifest.txt

  ############################## BUILD ##############################

    # Unpack source package
    dpkg-source -x *dsc

    pushd `find . -maxdepth 1 -type d | grep -vx .`

    /usr/lib/pbuilder/pbuilder-satisfydepends
    apt-get -y remove openjdk* maven2 || /bin/true

    if [ ! -z "$CODENAME" ]; then
      CODENAMETAG="~$CODENAME"
    fi
    VERSION=$(dpkg-parsechangelog | grep '^Version:' | awk '{print $2}')
    NEWVERSION=$VERSION$CODENAMETAG

    DEBEMAIL=info@cloudera.com \
      DEBFULLNAME="Cloudera Automatic Build System" \
      yes | dch --force-bad-version -v $NEWVERSION --distribution $CODENAME "EC2 Build ID $BUILD_ID"

    if [ -z "$DEBUILD_FLAG" ]; then
      DEBUILD_FLAG='-b'
    fi

    debuild --preserve-envvar FORREST_HOME -uc -us $DEBUILD_FLAG

    if [ $? -ne 0 ]; then
      send_email $PACKAGE $DEB_HOST_ARCH
    fi

    apt-get -y remove openjdk* maven2 || /bin/true

    popd 

    pwd

    ############################## UPLOAD ##############################

    # we don't want to upload back the source change list
    rm *_source.changes

    FILES=$(grep-dctrl -n -s Files '' *changes | grep . | awk '{print $5}')

    ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i /root/.ssh/static_vm_key root@${STAGING_HOST} "mkdir -p ${INTERIM_STAGING}/$BUILD_ID/binary/deb_${CODENAME}_${DEB_HOST_ARCH}"

    for f in $FILES *changes ; do
        scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i /root/.ssh/static_vm_key $f \
            root@${STAGING_HOST}:${INTERIM_STAGING}/$BUILD_ID/binary/deb_${CODENAME}_${DEB_HOST_ARCH}/$(basename $f) 
#        $S3CMD put $S3_BUCKET:build/$BUILD_ID/deb_${CODENAME}_${DEB_HOST_ARCH}/$(basename $f) $f x-amz-acl:public-read

    done

    # Leave /tmp/$BUILD_ID
    popd

    rm -rf /tmp/$BUILD_ID

  done

# Untrap, we're shutting down directly from here so the exit trap probably won't
# have time to do anything
if [ "x$INTERACTIVE" == "xFalse" ]; then
  trap - INT TERM EXIT
fi

copy_logs_s3

# If we're running on S3, shutdown the node
# (do the check so you can test elsewhere)
hostname -f | grep -q internal && shutdown -h now

} 2>&1 | tee /var/log/user.log
