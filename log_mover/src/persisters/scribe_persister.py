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
A persister to persist logs collected
by Scribe
"""
import logging
import os

# storage imports
import db.connection

# logging setup
logger = logging.getLogger(".")

class ScribePersister:
  """
  A class to persist any log fetched by Scribe
  """

  # the current filename
  CURRENT_FILENAME = '_current'

  # the mover string
  __mover = None
  # the log dir
  __log_dir = None

  # keeps track of if this mover has been
  # invoked or not
  __first_persist = False

  def init(self, mover, log_dir):
    """
    Initializes this persister

    @type  mover  : string
    @param mover  : the name of this mover
    @type  log_dir: string
    @param log_dir: the directory that Scribe logs
                    are in
    """
    self.__mover = mover
    self.__log_dir = log_dir

  def get_files(self):
    """
    Looks at the last_read and last_write
    tables to determine which files have already
    been written and which have been read but not
    yet written

    @rtype  : string list
    @return : the list of files to move, their full paths included
    """
    last_read = self._get_last_read()
    last_write = self._get_last_write()

    files = []
    # loop through all files in the log directory
    # and make sure they aren't the current file and
    # they fall, exclusively, between the last read
    # and the last write
    for file in os.listdir(self.__log_dir):
      if file < last_read and \
         (last_write == None or \
          file > last_write) and \
         file != self.__mover + self.CURRENT_FILENAME:
        files.append(file)

    return files

  def _get_last_read(self):
    """
    Gets the last read.  This method will
    throw an exception if the last_read
    table has not yet been populated by
    a log_to_db class

    @rtype : string (MySQL datetime format)
    @return: the date this was last read
    """
    conn = db.connection.connect()
    c = conn.cursor()

    c.execute("select l_r_file \
               from last_read \
               where l_r_mover = '%s'" %
               self.__mover)

    row = c.fetchone()
    return row[0]

  def _get_last_write(self):
    """
    Get the last write file, return None
    if nothing has been written yet

    @rtype : string (MySQL datetime format)
    @return: the date this was last read
    """
    conn = db.connection.connect()
    c = conn.cursor()

    c.execute("select l_w_file \
               from last_write \
               where l_w_mover = '%s'" %
               self.__mover)

    row = c.fetchone()
    if row != None:
      return row[0]
    else:
      self.__first_persist = True

  def update_last_write(self, files):
    """
    Given a list of files, updates the
    last write table to reflect what
    has been written

    @type  files: string list
    @param files: the list of files that were persisted
    """
    if len(files) != 0:
      # sort the files and grab the last
      # one, or the one with the largest
      # filename
      files = sorted(files)
      max = files[-1]

      conn = db.connection.connect()
      c = conn.cursor()

      # update if this mover has already
      # been instantiated
      if not self.__first_persist:
        c.execute("update last_write \
                   set l_w_file = '%(file)s' \
                   where l_w_mover = '%(mover)s'" %
                   {'file': max, 'mover': self.__mover})
      # insert if it hasn't been run yet
      else:
        c.execute("insert into last_write values \
                   ('%(mover)s', '%(file)s')" %
                   {'mover': self.__mover, 'file': max})

      conn.commit()
      c.close()
