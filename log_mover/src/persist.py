# (c) Copyright 2008 Cloudera, Inc.
"""
This file invokes persisters that copy logs to
HDFS and moves them locally to a TRASH folder

This file is invoked by a cron job
"""
import logging
from subprocess import Popen, PIPE
import os
import shutil

import settings

# pruner imports
import persisters.scribe_persister as scribe_persister

# logging setup
logger = logging.getLogger(".")
formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
logger.setLevel(logging.DEBUG)
file_handler = logging.FileHandler(os.path.join(settings.log_out, 'persist.log'))
file_handler.setFormatter(formatter)
logger.addHandler(file_handler)

# conf

action_classes = {'vmstat': scribe_persister.ScribePersister(),
                  'iostat': scribe_persister.ScribePersister(),
                  'net_io': scribe_persister.ScribePersister(),
                  'hadoop': scribe_persister.ScribePersister(),
                  'mpstat': scribe_persister.ScribePersister(),
                 }
"""
The classes that do the persisting
"""

def persist():
  """
  Uses action_classes to create a list of files to be moved.
  Then moves the files to HDFS, maintaining the exact
  directory structure.
  """
  for mover in settings.movers:
    logger.info("Persisting %s" % mover)

    action_classes[mover].init(mover, settings.log_locations[mover])
    # get the files to persist
    files = action_classes[mover].get_files()

    # creat the HDFS path to mimic the current log location
    create_hdfs_path(settings.log_locations[mover])

    # loop through each file, skip TRASH if it exists, and
    # copy each file to HDFS
    for file in files:
      if file != 'TRASH':
        path = os.path.join(settings.log_locations[mover], file)
        logger.info("Persisting %s" % path)

        # create the copy command
        command = settings.bin_path + " " + settings.copy_command + \
                  " " + path + " " + path

        # run the command
        p = Popen(command, shell=True, stdout=PIPE, stderr=PIPE)
        errors = p.stderr.readlines()

        if errors:
          logger.info(errors)

        # move the file to the trash
        move_to_trash(settings.log_locations[mover], file)

    # update the last write for this mover
    action_classes[mover].update_last_write(files)

def move_to_trash(log_dir, file):
  """
  Given a log directory and a file,
  move that file to the trash, which is in
  log_dir/TRASH
  """
  path = os.path.join(log_dir, file)
  trash_path = os.path.join(log_dir, 'TRASH')
  new_path = os.path.join(trash_path, file)
  logger.info("Moving %s to trash" % path)

  # create the trash directory if it doesn't exist
  if not os.path.isdir(trash_path):
    logger.info("Trash doesn't exist; creating %s" % trash_path)
    os.mkdir(trash_path)

  # move the file
  shutil.move(path, new_path)

def create_dir_if_not_exists(path):
  """
  Creates an HDFS directory if it doesn't
  already exist
  """
  p = Popen(settings.bin_path + " " + settings.ls_command + \
            " " + path, \
            shell=True, stdout=PIPE, stderr=PIPE)

  # if the directory doesn't exist
  if len(p.stderr.readlines()) != 0:
    logger.info("Creating HDFS directory %s" % path)
    # create the directory
    p2 = Popen(settings.bin_path + " " + settings.mkdir_command + \
               " " + path, \
               shell=True, stdout=PIPE, stderr=PIPE)

def create_hdfs_path(log_dir):
  """
  Given a log dir, recurses down the
  directory tree and makes sure that
  each directory exists in HDFS

  This essentially mirrors the log
  directory path in HDFS
  """
  parts = log_dir.split("/")

  # the place in the split that we are currently
  # looking at.  Starts at 1 because parts[0]
  # will be blank
  path_index = 1
  path = ''

  # loop until we have seen the whole path
  while path != log_dir and path+"/" != log_dir:
    path += "/" + parts[path_index]
    path_index += 1

    # create the directory if it doesn't exist
    create_dir_if_not_exists(path)

if __name__ == "__main__":
  persist()
