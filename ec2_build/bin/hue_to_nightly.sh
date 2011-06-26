#!/bin/bash
BUILD_ID=$1
BASE_DIR=$2

sudo rm -rf $BASE_DIR/hadoop-*
sudo rm -rf $BASE_DIR/hive-*
sudo rm -rf $BASE_DIR/hbase-*
sudo rm -rf $BASE_DIR/zookeeper-*
sudo rm -rf $BASE_DIR/sqoop-*
sudo rm -rf $BASE_DIR/pig-*
sudo rm -rf $BASE_DIR/oozie-*
sudo rm -rf $BASE_DIR/nightly_*
sudo rm -rf $BASE_DIR/flume-*

sudo cp -r ~ubuntu/gpg-home $BASE_DIR/apt
sudo cp -r ~ubuntu/gnupg $BASE_DIR/yum
sudo chmod -R 777 $BASE_DIR/apt/gpg-home
sudo chmod -R 777 $BASE_DIR/yum/gnupg

# Get the bits out of S3 buckets (copied from apt/update_repo.sh)

S3_BUCKET="cloudera-hue-freezer"
sudo -E -u www-data mkdir -p $BASE_DIR/$BUILD_ID/binary $BASE_DIR/$BUILD_ID/source
sudo -E -u www-data s3cmd sync s3://$S3_BUCKET/$BUILD_ID/binary $BASE_DIR/$BUILD_ID
sudo -E -u www-data s3cmd sync s3://$S3_BUCKET/$BUILD_ID/source $BASE_DIR/$BUILD_ID

# Deb
sudo -E -u www-data gpg-agent --daemon --write-env-file $BASE_DIR/.gpg-agent-info.apt --homedir $BASE_DIR/apt/gpg-home/ --allow-preset-passphrase
sudo -E -u www-data $(cat $BASE_DIR/.gpg-agent-info.apt) /usr/lib/gnupg2/gpg-preset-passphrase -v --preset -P D00pSigner F36A89E33CC1BD0F71079007327574EE02A818DD
export GPG_AGENT_CRAP=$(cat $BASE_DIR/.gpg-agent-info.apt)
sudo -E -u www-data GNUPGHOME=$BASE_DIR/apt/gpg-home/ $GPG_AGENT_CRAP $BASE_DIR/apt/update_repo.sh -s cloudera-hue-freezer -b $BUILD_ID -c cdh3 -r /var/www/archive_public/debian -d $BASE_DIR

# Yum
sudo -E -u www-data echo "%_gpg_name              Yum Maintainer" >> ~/.rpmmacros
sudo -E -u www-data gpg-agent --daemon --write-env-file $BASE_DIR/.gpg-agent-info.yum --homedir $BASE_DIR/yum/gnupg/ --allow-preset-passphrase
sudo -E -u www-data $(cat $BASE_DIR/.gpg-agent-info.yum) /usr/lib/gnupg2/gpg-preset-passphrase -v --preset -P abf9a621527cfbaebd0d7f2c23994dd8 5F14D39EF0681ACA6F044A43F90C0D8FE8F86ACD
export GPG_AGENT_CRAP=$(cat $BASE_DIR/.gpg-agent-info.yum)
sudo -E -u www-data GNUPGHOME=$BASE_DIR/yum/gnupg $GPG_AGENT_CRAP $BASE_DIR/yum/update_repos.sh -s $BASE_DIR/$BUILD_ID/ -c 3 -r /var/www/archive_public/redhat/ -p abf9a621527cfbaebd0d7f2c23994dd8
sudo -E -u www-data GNUPGHOME=$BASE_DIR/yum/gnupg $GPG_AGENT_CRAP $BASE_DIR/yum/update_repos.sh -s $BASE_DIR/$BUILD_ID/ -c 3 -r /var/www/archive_public/sles/11/x86_64/ -p abf9a621527cfbaebd0d7f2c23994dd8 -d sles11

for FILE in /var/www/archive_public/sles/11/x86_64/cdh/3/repodata/*.asc; do
  echo "Removing previous signature"
  sudo -E -u www-data GNUPGHOME=$BASE_DIR/yum/gnupg $GPG_AGENT_CRAP rm -f $FILE
done

pushd /var/www/archive_public/sles/11/x86_64/cdh/3/repodata/
for FILE in *; do
  echo "Signing $FILE"
  sudo -E -u www-data GNUPGHOME=$BASE_DIR/yum/gnupg $GPG_AGENT_CRAP gpg --armor --detach-sign --batch --passphrase 'abf9a621527cfbaebd0d7f2c23994dd8' $FILE
done
popd

sudo -E -u www-data /bin/bash ~ubuntu/hue-finalize-staging.sh -b $BUILD_ID -c 3 -r /var/www/archive_public/ -d $BASE_DIR

# Cleanup
sudo rm -rf $BASE_DIR/apt/gpg-home
sudo rm -rf $BASE_DIR/yum/gnupg

