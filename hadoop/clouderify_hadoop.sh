#!/bin/sh -x

set -e

DIR=$(dirname $0)
HADOOP_DIR=$DIR/..

git init
cd $HADOOP_DIR
for PATCH in `ls -1 $DIR/git-patches/* | sort` ; do
    git apply $PATCH
done
