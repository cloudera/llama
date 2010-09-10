#!/bin/bash
#
# Wrapper to drive release note HTML generation. Run as
#
# sh relnote-gen.sh /home/eli/cdh/relnote           \
#    /home/eli/src/cloudera/hadoop1                 \
#    8078e70b8916fe53139b07a87c92f743be04ba0a..HEAD \
#    "CDH 2" 0.20.1 hadoop-0.20.1+169.56
#
# This will generate hadoop-0.20.1+169.56.releasenotes.html
# in /home/eli/cdh/relnote using the git repo located at
# /home/eli/src/cloudera/hadoop1 using the above range
# specification. The release version (CDH 2) and Apache base
# version (0.20.1) are only for HTML generation.
#

# Arguments:
# $1 Directory to generate release notes into
# $2 git source directory
# $3 git range specification to generate notes for
# $4 Release version eg "CDH 2"
# $5 Base Hadoop verion eg "0.20.1"
# $6 CDH Hadoop version eg "hadoop-0.20.1+169.56"
function relnote_gen {
  local gen_dir=$1
  local commit_log=$gen_dir/$6-changes.log
  local relnote_file=$gen_dir/$6.releasenotes.html
  echo "pushd $2 >& /dev/null"
  pushd $2 >& /dev/null
  git log --pretty=oneline --no-color $3 > $commit_log
  popd >& /dev/null
  python ./tools/relnotes/relnotegen.py -l $commit_log -r "$4" -a $5 -c $6 > $relnote_file
}

relnote_gen $1 $2 $3 "$4" $5 $6
