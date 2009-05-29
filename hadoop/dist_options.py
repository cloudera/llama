#!/usr/bin/env python
#
# (c) Copyright 2009 Cloudera, Inc.

# Patches to apply
PATCH_DIR = "patches"
PATCHES = [
  'scribe_hadoop_trunk.patch',
  'nn+jt_cloudera_0.18.2_noheader.patch',
  'HADOOP-2536-0.18.2.patch',
  'HADOOP-4955.patch',
  'fairscheduler-0.18.3.patch',
  'fairscheduler-0.18.3-fix.patch',
  'HADOOP-4873-1.patch',
  'HADOOP-5154-v4.patch',
  'FairSchedulerStyled.patch',
  'HADOOP-3646-0.18.3.patch',
  'HADOOP-4918-0.18.3.patch',
  'HADOOP-3950.patch',
  'HADOOP-4346-0.18.3.patch',
  'HADOOP-5240-0.18.3.patch',
  'HADOOP-3655.patch',
  'HADOOP-5465-0.18.3.patch',
  'HADOOP-3327.patch',
  'HADOOP-3344.-Fix-libhdfs-build-to-use-autoconf-and-b.patch',
  'rerun_automake_aclocal_after_HADOOP-3344.patch',
  'HADOOP-5611-Add-some-missing-includes-to-c-code-t.patch',
  'HADOOP-5612-Add-chmod-rules-to-build.xml-to-make.patch',
  'include_pthread_in_pipes_examples.patch',
  'HADOOP-5518-branch18.patch',
  'HADOOP-5815-branch18.patch',
  'HADOOP-5844.patch',
  'HADOOP-1722-branch-0.18.patch',
  'HADOOP-5450.patch',
  'HADOOP-5114-branch-18.patch',
  'HADOOP-3663.patch',
  'HADOOP-3155-branch18.patch',
  'HADOOP-3361-0.18.3.patch',
  'HADOOP-4829-0.18.3.patch',
  'HADOOP-4377-hadoop-s3n-tmpdir.patch',
  'HADOOP-5170-tasklimits-v3-0.18.3.patch',
  'HADOOP-5175-dont-unpack-libjars-0.18.3.patch',
  'HADOOP-5613-s3exception-ioexception.patch',
  'HADOOP-5656-s3n-bytes-counters.patch',
  'HADOOP-5805-s3n-toplevel-buckets-io.patch',
  ]

# Things to make executable after applying the patches, since
# 'patch' can't track permissions
CHMOD_EXECUTABLE = [
  'src/native/config/config.sub',
  ]

scribe_directory = "src/contrib/scribe-log4j/lib/"
static_images_dir = "src/webapps/static/images/"

# Source files that need to be copied into the distribution.
# The destination directories will be mkdired if necessary.
SOURCE_DIR = "files"
COPY_FILES = [
  ('libfb303.jar', scribe_directory),
  ('libthrift.jar', scribe_directory),
  ('hsqldb.jar', 'lib/'),
  ('mysql-connector-java-5.0.8-bin.jar', 'lib/'),
  (['bluebar.png', 'logo.png'], static_images_dir),
  ('hadoop-config.sh', 'bin/'),
  ('junit-4.5.jar', 'lib/'),
  ('jets3t-0.6.1.jar', 'lib/'),
  ('sqoop', 'bin/'),
  ]

# Original files that should be removed from the final version because
# they were obsoleted by something added by COPY_FILES, with a different
# filename. Doesn't actually remove them; just renames them to '*.orig'
DELETE_FILES = [
  'lib/junit-3.8.1.jar',
  'lib/jets3t-0.6.0.jar',
  ]

