#!/bin/sh -x
# (c) Copyright 2009 Cloudera, Inc.

##SUBSTITUTE_VARS##
export BUILD_ID
export CDH_RELEASE
export INTERACTIVE
export BUILD_USER
export CODENAME
export MANIFEST_URL
export PACKAGES
export S3_BUCKET
export AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY

export OOZIE_SKIP_TEST_EXEC=true
export ARCH=$(uname -i)

export LOG_FILE=/var/log/user.log

cat > $HOME/.s3cfg << EOF
[default]
access_key = $AWS_ACCESS_KEY_ID
acl_public = False
bucket_location = US
debug_syncmatch = False
default_mime_type = binary/octet-stream
delete_removed = False
dry_run = False
encrypt = False
force = False
gpg_command = /usr/bin/gpg
gpg_decrypt = %(gpg_command)s -d --verbose --no-use-agent --batch --yes --passphrase-fd %(passphrase_fd)s -o %(output_file)s %(input_file)s
gpg_encrypt = %(gpg_command)s -c --verbose --no-use-agent --batch --yes --passphrase-fd %(passphrase_fd)s -o %(output_file)s %(input_file)s
gpg_passphrase = 
guess_mime_type = False
host_base = s3.amazonaws.com
host_bucket = %(bucket)s.s3.amazonaws.com
human_readable_sizes = False
preserve_attrs = True
proxy_host = 
proxy_port = 0
recv_chunk = 4096
secret_key = $AWS_SECRET_ACCESS_KEY
send_chunk = 4096
simpledb_host = sdb.amazonaws.com
use_https = False
verbosity = WARNING
EOF

function copy_logs_s3 {
      s3cmd put $LOG_FILE s3://$S3_BUCKET/build/$BUILD_ID/${CODENAME}-${ARCH}-user.log
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
Subject: $BUILD_ID $PACKAGE rpms failed for $TARGET_ARCH

The failure happened at $TIME on $HOST.

Check s3://$S3_BUCKET/build/$BUILD_ID/${CODENAME}-${TARGET_ARCH}-user.log for details

EOF

  cat $TMPFILE | sendmail -t
  rm -f $TMPFILE
}

if [ "x$INTERACTIVE" == "xFalse" ]; then
  trap "copy_logs_s3; hostname -f | grep -q internal && shutdown -h now;" INT TERM EXIT
fi

source /opt/toolchain/toolchain.sh
pushd /tmp
    wget http://archive.apache.org/dist/maven/binaries/apache-maven-3.0.2-bin.tar.gz
    tar zxvf apache-maven-*tar.gz
    export MAVEN_HOME=/tmp/apache-maven-3.0.2
    export PATH=$MAVEN_HOME/bin:$PATH
popd

yum install -y openssl-devel

{
############# BUILD PACKAGE ####################

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
  if (/^$PACKAGE-rpm/) {
    print \"Fetching \$F[1]...\\n\";
    system(\"/usr/bin/curl\", \"-s\", \"-o\", \$F[1], \$F[3]);
  }" manifest.txt

  # Satisfy build deps
  YUMINST="yum --nogpgcheck -y install"
  rpm -qRp *src.rpm | awk '{print $1}' | xargs $YUMINST

  # make build dir
  rm -Rf /tmp/topdir
  mkdir /tmp/topdir
  (cd /tmp/topdir && mkdir BUILD RPMS SOURCE SPECS SRPMS)

  for TARGET_ARCH in noarch $ARCH ; do
    rm -Rf /tmp/buildroot
    mkdir /tmp/buildroot
    rpmbuild --define "_topdir /tmp/topdir" --buildroot /tmp/buildroot --target $TARGET_ARCH --rebuild *src.rpm

    if [ $? -ne 0 ]; then
      send_email $PACKAGE $TARGET_ARCH
    fi
  done

  ############################## UPLOAD ##############################

  for arch_dir in /tmp/topdir/RPMS/*  ; do
    TARGET_ARCH=$(basename $arch_dir)
    for f in $arch_dir/*.rpm ; do
        s3cmd put $f s3://$S3_BUCKET/build/$BUILD_ID/rpm_${CODENAME}_${ARCH}/$(basename $f) 
    done
  done

  # Leave /tmp/$BUILD_ID
  popd

  rm -rf /tmp/$BUILD_ID

done
} 2>&1 | tee $LOG_FILE

# Untrap, we're shutting down directly from here so the exit trap probably won't
# have time to do anything
if [ "x$INTERACTIVE" == "xFalse" ]; then
  trap - INT TERM EXIT
fi

copy_logs_s3

# If we're running on S3, shutdown the node
# (do the check so you can test elsewhere)
hostname -f | grep -q internal && shutdown -h now
