# (c) Copyright 2008 Cloudera, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
"""
This file invokes pruners that prune
DB tables

This file is invoked by a cron job
"""
import logging
import os

import settings

# storage modules
import db.connection

# pruner imports
import pruners.general as general

# logging setup
logger = logging.getLogger(".")
formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
logger.setLevel(logging.DEBUG)
file_handler = logging.FileHandler(os.path.join(settings.log_out, 'prune.log'))
file_handler.setFormatter(formatter)
logger.addHandler(file_handler)

# conf

action_classes = {'vmstat': general.GeneralPruner(
                               settings.log_prune_date_col['vmstat']),
                  'iostat': general.GeneralPruner(
                               settings.log_prune_date_col['iostat']),
                  'net_io': general.GeneralPruner(
                               settings.log_prune_date_col['net_io']),
                  'hadoop': general.GeneralPruner(
                               settings.log_prune_date_col['hadoop']),
                  'mpstat': general.GeneralPruner(
                               settings.log_prune_date_col['mpstat']),
                 }
"""
The classes that will do the pruning
"""

def prune():
  """
  Loops through each action class, intiailizes them,
  then prunes them
  """
  for mover in settings.movers:
    logger.info("Pruning %s" % mover)
    # NOTE: settings.log_lifetime[mover] might be -1, which means
    # no pruning should be done
    prune_time = settings.log_lifetime[mover]
    action_classes[mover].init(mover, prune_time)
    rows = action_classes[mover].prune()
    if prune_time != -1:
      logger.info("\tpruned %d rows" % rows)
    else:
      logger.info("\tnot configured to prune")

if __name__ == "__main__":
  prune()
