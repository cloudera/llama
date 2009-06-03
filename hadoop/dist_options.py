#!/usr/bin/env python
#
# (c) Copyright 2009 Cloudera, Inc.

# Patches to apply
PATCH_DIR = "patches"
PATCHES = [
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
  (['images/feed.png',
   'images/logo.png',
   'images/no_arrow.png',
   'images/arrow.png',
   'images/bluebar.png',
   'images/favicon.ico',
   'images/more_arrow.png'], static_images_dir),
  ('hadoop-config.sh', 'bin/'),
  ('junit-4.5.jar', 'lib/'),
  ('jets3t-0.6.1.jar', 'lib/'),
  ('sqoop', 'bin/'),
  ]

# Original files that should be removed from the final version because
# they were obsoleted by something added by COPY_FILES, with a different
# filename. Doesn't actually remove them; just renames them to '*.orig'
DELETE_FILES = [
  ]

