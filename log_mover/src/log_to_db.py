# (c) Copyright 2008 Cloudera, Inc.
"""
This file invokes log_to_dbs that read
the location of their logs from a configuration
file and move the unread contents in to a DB.

This file is invoked by a cron job
"""
import logging
import datetime
import os

import settings

# storage modules
import db.connection

# log mover imports
import log_to_dbs.scribe_unix_cmd as scribe_unix_cmd
import log_to_dbs.scribe_hadoop as scribe_hadoop

# unix command log parsers
import log_to_dbs.parsers.vmstat as vmstat_parser
import log_to_dbs.parsers.iostat as iostat_parser
import log_to_dbs.parsers.net_io as net_io_parser
import log_to_dbs.parsers.mpstat as mpstat_parser

# logging setup
logger = logging.getLogger(".")
formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
logger.setLevel(logging.DEBUG)
console_handler = logging.StreamHandler()
console_handler.setLevel(logging.DEBUG)
logger.addHandler(console_handler)
file_handler = logging.FileHandler(os.path.join(settings.log_out, 'log_to_db.log'))
file_handler.setFormatter(formatter)
logger.addHandler(file_handler)

# conf

action_classes = {'vmstat':
                     scribe_unix_cmd.ScribeUnixCMDLogToDB(
                              vmstat_parser.VMStatParser(),
                              settings.log_prefixes['vmstat']),
                  'iostat':
                     scribe_unix_cmd.ScribeUnixCMDLogToDB(
                              iostat_parser.IOStatParser(),
                              settings.log_prefixes['iostat']),
                  'net_io':
                     scribe_unix_cmd.ScribeUnixCMDLogToDB(
                              net_io_parser.NetIOParser(),
                              settings.log_prefixes['net_io']),
                  'hadoop':
                     scribe_hadoop.ScribeHadoopLogToDB(
                              settings.log_prefixes['hadoop']),
                  'mpstat':
                     scribe_unix_cmd.ScribeUnixCMDLogToDB(
                              mpstat_parser.MPStatParser(),
                              settings.log_prefixes['mpstat']),
                 }
"""
the association between movers and
the class that will act for them
"""

def log_to_db():
  """
  loop through each mover, initialize the class
  to move logs to the DB, and then actually do the
  copy
  """
  for mover in settings.movers:
    logger.info("Moving %s" % mover)
    action_classes[mover].init(mover, settings.log_locations[mover])
    action_classes[mover].log_to_db()
    action_classes[mover].store_progress()

if __name__ == "__main__":
  log_to_db()
