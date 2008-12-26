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
A general pruner
"""
import logging
import common

# storage imports
import db.connection

# logging setup
logger = logging.getLogger(".")

class GeneralPruner:
  """
  A general pruning class
  """

  __log_lifetime = None
  __col_name = None
  __mover = None

  def __init__(self, col_name):
    """
    Remembers the column name
    to compare dates against
    """
    self.__col_name = col_name

  def init(self, mover, log_lifetime):
    """
    initializes this pruner with the
    log lifetime, in seconds.  Also
    initializes the mover

    note that log_lifetime may be
    -1, signifying that the log
    should never be pruned

    @type  mover:        string
    @param mover:        the name of this mover
    @type  log_lifetime: number
    @param log_lifetime: the time logs should live
    """
    self.__log_lifetime = log_lifetime
    self.__mover = mover

  def prune(self):
    """
    Prune old rows and report back the number
    of rows that were deleted

    @rtype : number
    @return: the number of items pruned
    """
    if self.__log_lifetime == -1:
      return

    # get the threshold date.  That is, the earliest
    # date that logs which are newer than should be
    # saved.  Returns the number of pruned items
    old_date = common.get_threshold_date(self.__log_lifetime)

    conn = db.connection.connect()
    c = conn.cursor()

    # get the initial number of rows
    c.execute("select count(*) from %s" % self.__mover)
    row = c.fetchone()
    start = row[0]

    # delete all old logs
    c.execute("delete from %(mover)s \
               where %(col)s < '%(date)s'" %
                      {'mover': self.__mover,
                       'col': self.__col_name,
                       'date': str(old_date)})

    # get the new number of rows
    c.execute("select count(*) from %s" % self.__mover)
    row = c.fetchone()
    end = row[0]

    conn.commit()
    c.close()

    return start - end
