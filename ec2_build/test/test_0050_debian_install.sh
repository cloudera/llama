#!/bin/bash -x
# (c) Copyright 2009 Cloudera, Inc.

set -e

############################## SETUP BUILD ENV ##############################

# Install things needed to build
export DEBIAN_FRONTEND=noninteractive

export CDH_REPO_HOST="$1"
export CDH_REPO_VERSION="$2"

# Make sure this script only runs on debian/ubuntu
if [ ! -e "/etc/debian_version" ]; then
  echo "This script is made for debian-like OS. Exiting"
  exit 0
fi

# The Karmic AMI is from Canonical instead of Alestic. They don't enable multiverse by
# default. We need multiverse for sun-java6-jdk.
if [ "$(lsb_release -c -s)" = "karmic" ]; then
  sed -i 's/universe/universe multiverse/' /etc/apt/sources.list
fi

# Canoncial moved Sun Java into the partner repo. Pull in the multiverse just in case.
if [ "$(lsb_release -c -s)" = "lucid" ]; then
  sed -i 's/universe/universe multiverse/' /etc/apt/sources.list
  add-apt-repository "deb http://archive.canonical.com/ lucid partner"
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
apt-get -y install debconf-utils python-apt

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


# Add Cloudera's GPG key
curl -s http://archive.cloudera.com/debian/archive.key | apt-key add -

echo deb http://${CDH_REPO_HOST}/debian $(lsb_release -c -s)-cdh${CDH_REPO_VERSION} contrib >> /etc/apt/sources.list

apt-get update

apt-cache search hadoop
apt-get install -y hadoop hadoop-0.20-conf-pseudo hadoop-0.20-datanode hadoop-0.20-doc hadoop-0.20-jobtracker hadoop-0.20-namenode hadoop-0.20-native hadoop-0.20-pipes hadoop-0.20-secondarynamenode hadoop-0.20-tasktracker hadoop-0.20-source hadoop-0.20-debuginfo hadoop-0.20-sbin


echo "Done."
exit 0
