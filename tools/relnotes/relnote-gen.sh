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
# $5 Base Project verion eg "0.20.1"
# $6 CDH Project version eg "hadoop-2.0.0-cdh4.1.0-SNAPSHOT"
# $7 CDH Project Name ed "Apache Hadoop"
# $8 CDH Packaging repository source directory
# $9 Base project name
# ${10} Packaging git range
# ${11} Since-last-release range.
set -ex
function relnote_gen {
  local gen_dir=$1
  local commit_log=$gen_dir/$6-changes.log
  local commit_since_last_log=$gen_dir/$6-since-last-release-changes.log
  local package_commit_log=$gen_dir/$6-package-changes.log
  local package_commit_since_last_log=$gen_dir/$6-package-since-last-release-changes.log
  local changes_file=$gen_dir/$6.CHANGES.txt
  local changes_since_last_file=$gen_dir/$6.since.last.release.CHANGES.txt
  local package_changes_file=$gen_dir/$6.package.CHANGES.txt
  local package_changes_since_last_file=$gen_dir/$6.package.since.last.release.CHANGES.txt
  local relnote_file=$gen_dir/$6.releasenotes.html
  local relnote_since_last_file=$gen_dir/$6.since.last.release.releasenotes.html
  echo "pushd $2 >& /dev/null"
  if [ ! -d $gen_dir ]; then
    mkdir -p $gen_dir
  fi
  virtualenv --clear $gen_dir/virtualenv
  . $gen_dir/virtualenv/bin/activate
  pip install pymongo

  pushd $2 >& /dev/null
  git log --pretty=oneline --no-color $3 > $commit_log
  git log --pretty=medium --no-color $3 > $changes_file

  local previous_release=$(echo ${11} | perl -pe 's/\.\.HEAD//')
  
  # Only do the since-last-release changes if the previous release is an ancestor of the current head
  if git rev-list HEAD | grep -q $(git rev-parse $previous_release); then 
      git log --pretty=oneline --no-color ${11} > $commit_since_last_log
      git log --pretty=medium --no-color ${11} > $changes_since_last_file
  fi
  popd >& /dev/null
  pushd $8 >& /dev/null
  git ls-files | grep -E "(common|deb|rpm)/$9" | xargs git log --pretty=oneline --no-color ${10} > $package_commit_log
  git ls-files | grep -E "(common|deb|rpm)/$9" | xargs git log --pretty=medium --no-color ${10} > $package_changes_file
  git ls-files | grep -E "(common|deb|rpm)/$9" | xargs git log --pretty=oneline --no-color ${11} > $package_commit_since_last_log
  git ls-files | grep -E "(common|deb|rpm)/$9" | xargs git log --pretty=medium --no-color ${11} > $package_changes_since_last_file
  popd >& /dev/null
  python ./tools/relnotes/relnotegen.py -l $commit_log -r "$4" -a $5 -c $6 -n "$7" > $relnote_file
  if [ -f $commit_since_last_log ]; then
      python ./tools/relnotes/relnotegen.py -l $commit_since_last_log -r "$4" -a $5 -c $6 -n "$7" --since_last > $relnote_since_last_file
  fi
  deactivate
}

relnote_gen $1 $2 $3 "$4" $5 $6 "$7" $8 $9 "${10}" "${11}"
