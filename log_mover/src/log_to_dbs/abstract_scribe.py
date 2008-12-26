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
An abstract class that provides functionality
to read scribe log files
"""
import logging
import sys
import os

# storage imports
import db.connection

import settings

# logging setup
logger = logging.getLogger(".")

class AbstractScribeLogToDB:
  """
  An abstract class to deal with scribe
  log files
  """

  # constants
  BUFFER_SIZE = settings.scribe_buffer_size

  # the mover string
  __mover = None
  # the log dir
  __log_dir = None

  # the current file that needs to be read
  __current_filename = None
  # the current offset where reading should
  # start from
  __current_offset = None

  # keeps track of whether or not this is the
  # first time this stat has been tracked
  __first_read = False

  # the number of bytes that have been read
  __bytes_read = 0

  # the prefix of the filename
  __file_prefix = None

  def _get_last_read(self):
    """
    Queries the last_read table
    to understand what filename
    and offset was read up to.

    @rtype : tuple
    @return: a filename, offest tuple
    """
    conn = db.connection.connect()
    c = conn.cursor()

    c.execute("select * from last_read \
               where l_r_mover = '%s'" %
               self.__mover)

    row = c.fetchone()
    # if this class has been invoked before
    if row != None:
      filename = row[1]
      offset = int(row[2])
    # otherwise, use the starting values
    else:
      filename = self.__file_prefix+"00000"
      offset = 0
      self.__first_read = True
    return filename, offset

  def __init__(self, file_prefix):
    """
    Initializes the file prefix

    @type  file_prefix: string
    @param file_prefix: the file prefix for each Scribe log
    """
    self.__file_prefix = file_prefix

  def init(self, mover, log_dir):
    """
    Given a mover and a log directory, initialize
    this class in preparation for actually copying
    logs to a DB

    @type  mover  : string
    @param mover  : the name of this mover
    @type  log_dir: string
    @param log_dir: the location of the Scribe logs for
                    this mover
    """
    self.__mover = mover
    self.__log_dir = log_dir

    # get the last read information
    self.__current_filename, self.__current_offset = \
           self._get_last_read()

  def log_to_db(self):
    """
    Copies the log specified at initialization time

    Does this by getting the last read information
    and reading the Scribe log.  Once the log has
    been fully read, then it starts to read the
    next file, stopping when all files have been
    read and ignoring the *_current file.
    """
    self._read_file(os.path.join(
             self.__log_dir, self.__current_filename),
             self.__current_offset)

    logger.info("Read and processed %d bytes" % self.__bytes_read)

  def _read_file(self, path, offset):
    """
    Given a path to a log file and an offset to that file,
    read the file and output parsed data to a DB

    @type  path:   string
    @param path:   a path to a log file
    @type  offset: number
    @param offset: the offset in to that file
    """
    logger.info("Reading from %(path)s at offset %(offset)d" %
                      {'path': path, 'offset': offset})

    # open the file for read only and jump to the
    # offset that we're interested in
    f = os.open(path, os.O_RDONLY)
    os.lseek(f, offset, 0)

    # prime the buffer, update the offset
    # and track the number of bytes that have
    # been read
    buffer = os.read(f, self.BUFFER_SIZE)
    self.__current_offset += len(buffer)
    self.__bytes_read += len(buffer)

    # because we're using an arbitrary
    # buffer length, we are not guaranteed
    # to see entire lines in the buffer.
    # prev_line will have the previous line
    # from the previous buffer chunk
    prev_line = None
    while buffer != '':
      lines = buffer.split("\n")

      # if there exists a previous line, then
      # append the first line to the previous line
      if prev_line != None:
        lines[0] = prev_line + lines[0]

      # if by chance we read all the way to the end
      # of a line in a single buffer, then set
      # prev_line to None
      if lines[-1] == '':
        prev_line = None
      # otherwise update prev_line to have the last
      # incomplete line in this buffer
      else:
        prev_line = lines[-1]
      # get rid of the last line, whether it be empty
      # or incomplete
      del lines[-1]

      try:
        # abstract call
        self.handle_lines(lines)
      except:
        logger.error("Exception caught while moving %s" % self.__mover)
        logger.error(lines)

      # read the next buffer chunk, etc
      buffer = os.read(f, self.BUFFER_SIZE)
      self.__current_offset += len(buffer)
      self.__bytes_read += len(buffer)

    # once the file has been read, close it
    os.close(f)
    # check to see if another file needs to be
    # read
    next_file = self.get_next(path)
    next_path = os.path.join(self.__log_dir, next_file)
    # if the next file exist and its not *_current
    if os.path.isfile(next_path) and \
       next_file != self.__mover + "_current":
      # recurse - note that we shouldn't be worried about
      # blowing the stack because there will not be a case
      # when an insane amount of files will need to be
      # processed at the same time
      self.__current_filename = next_file
      self.__current_offset = 0
      self._read_file(next_path, 0)

  def handle_lines(self, lines):
    """
    Abstract method that should parse and store lines
    that have been extracted from a Scribe log

    @type  lines: string list
    @param lines: the lines that be parsed / stored
    """
    raise NotImplementedError

  def get_next(self, path):
    """
    Given a path, return the next filename
    and do not check if the filename exists.
    Just take the current path and add "1"
    to the end, ensuring formating, etc

    @type  path: string
    @param path: the path of the log file that
                 was just read
    @rtype     : string
    @return    : the next Scribe log
    """
    # get the filename and grab the number at the
    # end of it
    head, tail = os.path.split(path)
    current_num = int(tail.split("_")[-1])
    current_num += 1
    current_num = str(current_num)
    # in the case that the number is less than
    # 5 digits, add leading zeros
    while len(current_num) != 5:
      current_num = "0" + current_num
    next_file = self.__file_prefix + current_num
    return next_file

  def store_progress(self):
    """
    Updates the last_read table with the filename
    that was last read and the offset that was last
    analyzed
    """
    conn = db.connection.connect()
    c = conn.cursor()

    # insert if this is the first time this mover
    # has been invoked
    if self.__first_read:
      c.execute("insert into last_read values \
                 ('%(mover)s', '%(filename)s', '%(offset)d')" %
                 {'mover': self.__mover,
                  'filename': self.__current_filename,
                  'offset': self.__current_offset})
    # otherwise just update
    else:
      c.execute("update last_read set \
                 l_r_file = '%(filename)s', \
                 l_r_offset = '%(offset)d' \
                 where l_r_mover = '%(mover)s'" %
                 {'filename': self.__current_filename,
                  'offset': self.__current_offset,
                  'mover': self.__mover})

    conn.commit()
    c.close()
