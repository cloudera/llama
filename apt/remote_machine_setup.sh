#!/bin/sh
# (c) Copyright 2009, Cloudera, inc.
set -e

if [ -z "$1" ]; then
  echo "usage: $0 <ec2 instance hostname>"
  exit 1
fi

if [ -z "$AWS_ACCESS_KEY_ID" -o -z "$AWS_SECRET_ACCESS_KEY" ]; then
  echo "must have AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY env vars set"
  exit 1
fi

EC2_HOST=$1
APT_DIR=`dirname $0`

# Set up apt user with home dir
ssh $EC2_HOST "useradd apt -m -d /mnt/apt || true"
rsync -avz $APT_DIR/ root@$EC2_HOST:/mnt/apt/
ssh $EC2_HOST "cp -a /root/.ssh /mnt/apt/ && chown -R apt.apt /mnt/apt"

# Set up S3 credentials for s3cfg
echo "
[default]
access_key = $AWS_ACCESS_KEY_ID
secret_key = $AWS_SECRET_ACCESS_KEY
" | ssh apt@$EC2_HOST "cat > ~/.s3cfg"

# Set up necessary packages
ssh $EC2_HOST "aptitude update && aptitude -y install s3cmd reprepro pinentry-curses gnupg-agent"

# Run the setup script there as the apt user
ssh apt@$EC2_HOST "./setup_apt_machine.sh"
