#!/usr/bin/env python
#
# (c) Copyright 2009 Cloudera, Inc.

# Patches to apply
PATCH_DIR = "patches"
PATCHES = [
  'scribe_hadoop_trunk_v5.patch',
  'nn+jt_cloudera_0.18.2_noheader.patch',
  'HADOOP-2536-0.18.2.patch',
  'HADOOP-4955.patch',
  'fairscheduler-0.18.3.patch',
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
  'HADOOP-3327.patch'
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
  (['bluebar.png', 'logo.png'], static_images_dir)
  ]
