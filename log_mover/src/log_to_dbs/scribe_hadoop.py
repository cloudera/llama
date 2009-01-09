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
A file that deals with Hadoop logs that
are collected by Scribe
"""
import re

import abstract_scribe
import common

import db.connection

class ScribeHadoopLogToDB(abstract_scribe.AbstractScribeLogToDB):
  """
  A LogToDB class that moves hadoop logs

  It does so by using the last_read table
  to learn what was read when this class was
  last invoked

  """

  # this variable tracks the last object
  # that was parsed and stored
  # so we can keep track of the extra
  # information to come
  __last_parsed = None

  # this variable exists to keep track of
  # the order of extra files, so we can
  # sort them to get their original order
  __count = 0

  # An example matching line:
  #  server10.cloudera.com org.apache.hadoop.mapred.Merger$MergeQueue
  #  08/11/25 12:12:33 INFO mapred.Merger: Down to the last
  #  merge-pass, with 2 segments left of total size: 3141424 bytes
  LOG_MATCH = re.compile("^[a-zA-Z0-9.]+ [a-z-A-Z0-9.?$]+ [0-9]{2}/[0-9]{2}/[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")

  # no need for a custom constructor

  def handle_lines(self, lines):
    """
    Implementation of the abstract method read
    a series of lines that were read from
    scribe.  The size of these lines is
    dependant on the lines themselves along with
    the BUFFER_SIZE constant defined in
    I{abstract_scribe.AbstractScribeLogToDB}

    Some Hadoop logs follow the standard log pattern, and others
    are just overflow from the previous log.  For example,
    Exceptions are overflow, along with a few other recurring
    types of messages.

    The non-standard, or secondary, logs are stored in the
    I{hadoop_extras} table.

    @type  lines: string list
    @param lines: lines of log messages
    """
    # loop through each line
    for line in lines:
      # if this is a normal log
      if self.LOG_MATCH.search(line):
        # process the standard log
        self.__last_parsed = self._process_std_line(line)
        self._store_parsed(self.__last_parsed)

        self.__count = 0
      else:
        if self.__last_parsed != None:
          self._store_extra(self.__last_parsed, line, self.__count)
          self.__count += 1

  @staticmethod
  def _store_extra(parsed, line, count):
    """
    Stores extra information as long as the
    information isn't blank.  Creates a foreign
    key to the previously parsed real log, along
    with a count for sorting purposes

    Escapes the line by replacing "'" with "\'"

    @type  parsed: dictionary
    @param parsed: the previous normal line in
                   parsed form
    @type  line  : string
    @param line  : the extra line to be stored
    @type  count : number
    @param count : the line count for this log
    """

    if line != '' and parsed['h_level'] == 'ERROR':
      conn = db.connection.connect()
      c = conn.cursor()

      line = line.replace("'", "\\'")

      sql = "insert into hadoop_extras values \
             (%(h_id)d, %(count)d, '%(line)s')" % \
             {'h_id': parsed['h_id'],
              'count': count,
              'line': line,
             }

      c.execute(sql)

      conn.commit()
      c.close()

  @staticmethod
  def _store_parsed(parsed):
    """
    Stores the parsed data, only if
    the level is ERROR

    Also adds a "h_id" field to the
    statistic

    @type  parsed: dictionary
    @param parsed: the parsed log message
    """
    if parsed['h_level'] == 'ERROR':
      conn = db.connection.connect()

      c = conn.cursor()

      cols = common.get_key_cols(parsed)
      vals = common.get_values(parsed)

      # special case the ID
      cols = 'h_id,' + cols
      vals = 'NULL,' + vals

      # ORM it!
      sql = "insert into hadoop (%(cols)s) values \
                        (%(vals)s)" % \
                        {'cols': cols,
                         'vals': vals}

      c.execute(sql)

      # add the 'h_id' field so any trailing data
      # can reference this hadoop log
      c.execute('select last_insert_id()')
      row = c.fetchone()
      parsed['h_id'] = row[0]

      conn.commit()
      c.close()

  @staticmethod
  def _process_std_line(line):
    """
    Handle a standard line

    @type  line: string
    @param line: the log message to be parsed
    """

    parsed = {}

    parts = line.split(" ")

    cor = {0: 'h_hostname',
           1: 'h_class',
           2: 'h_date', # special case because
           3: 'h_date', # date spans two parts
           4: 'h_level',
           5: 'h_category',
           6: 'h_message',
           }
    i = 0
    for part in parts:
      if part == '':
        continue

      # handle the special case for the date
      # spanning two parts
      if i == 3:
        parsed[cor[i - 1]] += ' ' + part
      elif i <= 6:
        if i == 5:
          # get rid of the training comma on
          # the category
          parsed[cor[i]] = part[:-1]
        else:
          parsed[cor[i]] = part
      # this is the message
      else:
        parsed[cor[6]] += ' ' + part

      i += 1

    return parsed
