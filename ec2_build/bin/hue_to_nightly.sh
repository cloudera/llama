#!/bin/bash
BUILD_ID=$1
sudo rm -rf /tmp/hadoop-*
sudo rm -rf /tmp/hive-*
sudo rm -rf /tmp/hbase-*
sudo rm -rf /tmp/zookeeper-*
sudo rm -rf /tmp/sqoop-*
sudo rm -rf /tmp/pig-*
sudo rm -rf /tmp/oozie-*
sudo rm -rf /tmp/nightly_*
sudo rm -rf /tmp/flume-*

sudo cp -r ~ubuntu/gpg-home /tmp/apt
sudo cp -r ~ubuntu/gnupg /tmp/yum
sudo chmod -R 777 /tmp/apt/gpg-home
sudo chmod -R 777 /tmp/yum/gnupg

# Get the bits out of S3 buckets (copied from apt/update_repo.sh)
BASE_DIR="/tmp"
S3_BUCKET="cloudera-hue-freezer"
mkdir -p $BASE_DIR/$BUILD_ID/binary
mkdir -p $BASE_DIR/$BUILD_ID/source
s3cmd sync s3://$S3_BUCKET/$BUILD_ID/binary $BASE_DIR/$BUILD_ID
s3cmd sync s3://$S3_BUCKET/$BUILD_ID/source $BASE_DIR/$BUILD_ID

# Deb
sudo -E -u www-data gpg-agent --daemon --write-env-file /tmp/.gpg-agent-info.apt --homedir /tmp/apt/gpg-home/ --allow-preset-passphrase
sudo -E -u www-data $(cat /tmp/.gpg-agent-info.apt) /usr/lib/gnupg2/gpg-preset-passphrase -v --preset -P D00pSigner F36A89E33CC1BD0F71079007327574EE02A818DD
export GPG_AGENT_CRAP=$(cat /tmp/.gpg-agent-info.apt)
sudo -E -u www-data GNUPGHOME=/tmp/apt/gpg-home/ $GPG_AGENT_CRAP /tmp/apt/update_repo.sh -s cloudera-hue-freezer -b $BUILD_ID -c cdh3 -r /var/www/archive_public/debian

# Yum
sudo -E -u www-data echo "%_gpg_name              Yum Maintainer" >> ~/.rpmmacros
sudo -E -u www-data gpg-agent --daemon --write-env-file /tmp/.gpg-agent-info.yum --homedir /tmp/yum/gnupg/ --allow-preset-passphrase
sudo -E -u www-data $(cat /tmp/.gpg-agent-info.yum) /usr/lib/gnupg2/gpg-preset-passphrase -v --preset -P abf9a621527cfbaebd0d7f2c23994dd8 5F14D39EF0681ACA6F044A43F90C0D8FE8F86ACD
export GPG_AGENT_CRAP=$(cat /tmp/.gpg-agent-info.yum)
sudo -E -u www-data GNUPGHOME=/tmp/yum/gnupg $GPG_AGENT_CRAP /tmp/yum/update_repos.sh -s /tmp/$BUILD_ID/ -c 3 -r /var/www/archive_public/redhat/ -p abf9a621527cfbaebd0d7f2c23994dd8
sudo -E -u www-data GNUPGHOME=/tmp/yum/gnupg $GPG_AGENT_CRAP /tmp/yum/update_repos.sh -s /tmp/$BUILD_ID/ -c 3 -r /var/www/archive_public/sles/11/x86_64/ -p abf9a621527cfbaebd0d7f2c23994dd8 -d sles11

for FILE in /var/www/archive_public/sles/11/x86_64/cdh/3/repodata/*.asc; do
  echo "Removing previous signature"
  sudo -E -u www-data GNUPGHOME=/tmp/yum/gnupg $GPG_AGENT_CRAP rm -f $FILE
done

pushd /var/www/archive_public/sles/11/x86_64/cdh/3/repodata/
for FILE in *; do
  echo "Signing $FILE"
  sudo -E -u www-data GNUPGHOME=/tmp/yum/gnupg $GPG_AGENT_CRAP gpg --armor --detach-sign --batch --passphrase 'abf9a621527cfbaebd0d7f2c23994dd8' $FILE
done
popd

sudo -E -u www-data /bin/bash ~ubuntu/hue-finalize-staging.sh -b $BUILD_ID -c 3 -r /var/www/archive_public/

# Cleanup
sudo rm -rf /tmp/apt/gpg-home
sudo rm -rf /tmp/yum/gnupg

