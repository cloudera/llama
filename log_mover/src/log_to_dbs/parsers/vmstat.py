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
# storage imports
import db.connection

import common

class VMStatParser:
  """
  A parser to parse 'vmstat' collected by
  Scribe
  """

  # sample output directory from the command line):
  # procs -----------memory---------- ---swap-- -----io---- --system-- -----cpu------
  # r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st
  # 0  0    180 15152948 188928 223196    0    0   159   187    0    0  2  0 98  0  0

  def process_line(self, line):
    """
    parse a line and return vmstats

    @type  line: string
    @param line: a log message line
    @rtype     : dictionary
    @return    : a parsed dictionary
    """
    # first get key, value pairs
    parts = line.split(chr(0))
    # split the first pair for purposes
    # of getting the hostname and the date
    first_parts = parts[0].split(" ")

    stats = {}

    # get the host and the date
    host = first_parts[0]
    date = first_parts[1] + " " + first_parts[2]

    stats['v_hostname'] = host
    stats['v_date'] = date

    # overwrite the hostname and date
    # so that only the first true vmstat
    # item exists.  The for loop
    # seeks to find the value, because
    # it could have any number of
    # arbitrary spaces
    i = 0
    for part in first_parts:
      # 3 is used because item 0 is
      # the hostname, 1 is the date
      # and 2 is the time
      if part != '' and i >= 3:
        break

      i += 1

    parts[0] = first_parts[i] + " " \
               + first_parts[i+2] + " " + first_parts[i+3]
    # loop through all parts, parse each part
    # and add the key, value pair to the stats dict
    for part in parts:
      key, value = self._get_stat(part)
      if key != None and value != None:
        stats[key] = value

    return stats

  def _get_stat(self, part):
    """
    given a single part, or key, value
    vmstat pair, return a key, value
    tuple

    @type  part: string
    @param part: a single part of vmstat output
    @rtype     : tuple
    @return    : a key, value tuple
    """
    part = part.strip()
    parts = part.split(" ")
    # ignore empty pairs
    if len(parts) == 1:
      return None, None
    value = parts[0]
    # fence post problem to deal with
    # keys that have spaces in them
    key = parts[1]
    for i in range(len(parts)-2):
      key += " " + parts[i+2]
    return key.strip(), value.strip()

  def store_line(self, stats):
    """
    Given statitistics, store them in to MySQL
    with an ORM mechanism

    @type  stats: dictionary
    @param stats: a parsed dictionary
    """
    conn = db.connection.connect()

    # this function will deal with keys having spaces
    # and capital letters, etc
    common.store_orm('vmstat', stats, conn)
