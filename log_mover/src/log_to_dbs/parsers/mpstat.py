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

class MPStatParser:
  """
  A parser to parse 'mpstat' collected by
  Scribe.
  """

  # sample output directory from the command line):
  # Linux 2.6.18-92.el5 (server10.cloudera.com)         12/01/2008
  #
  # 11:24:24 PM  CPU   %user   %nice    %sys %iowait    %irq   %soft  %steal   %idle    intr/s
  # 11:24:24 PM  all    1.54    0.00    0.19    0.44    0.01    0.05    0.00   97.76   1188.07

  def process_line(self, line):
    """
    parse a line and return mpstats

    returns None if the line is invalid

    @type  line: string
    @param line: the log message
    @rtype     : dictionary
    @return    : a parsed dictionary
    """
    # first separate out the original lines
    parts = line.split(chr(0))

    # split the line for purposes
    # of getting the hostname and the date
    first_parts = parts[0].split(" ")

    stats = {}

    # get the host and the date
    host = first_parts[0]
    date = first_parts[1] + " " + first_parts[2]

    stats['m_hostname'] = host
    stats['m_date'] = date

    # find the line with the 'all' string,
    # which represents the line with all
    # the CPU data
    for part in parts:
      if part.find('all') != -1:
        self._get_stats(stats, part)
        break

    return stats

  @staticmethod
  def _get_stats(stats, line):
    """
    Given the line that contains CPU
    usage information, add keys and values
    to the 'stats' dictionary

    @type  line: string
    @param line: the log message
    """
    parts = line.split(" ")
    # this does not start at 0, because the line
    # with CPU usage has 3 items
    # that come before the CPU usage
    cor = {2:  'm_user',
           3:  'm_nice',
           4:  'm_sys',
           5:  'm_iowait',
           6:  'm_irq',
           7:  'm_soft',
           8:  'm_steal',
           9: 'm_idle',
           10: 'm_intr_s',
           }
    i = 0
    for part in parts:
      if part == '':
        continue
      # again, this logic is in place
      # because we aren't interested in
      # the first fiew parts of the
      # cpu usage line
      elif not cor.has_key(i):
        i += 1
        continue

      # some logs have PM or AM after the time
      # so this if statements ignores those
      # different lines.  I'm not sure why
      # some vmstat output has this data and
      # some doesn't, though
      if part == 'PM' or part == 'AM' or part == 'all':
        continue

      stats[cor[i]] = float(part)
      i += 1

  def store_line(self, stats):
    """
    Given statitistics, store them in to MySQL
    with an ORM mechanism

    @type  stats: dictionary
    @param stats: the parsed line to be stored
    """
    conn = db.connection.connect()

    common.store_orm('mpstat', stats, conn)
