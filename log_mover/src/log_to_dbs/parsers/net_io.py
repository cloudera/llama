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
import settings

class NetIOParser:
  """
  A parser to parse 'cat /proc/net/dev' collected by
  Scribe.  It only collects data from one network
  card, specified by a constant
  """

  # sample output directory from the command line):
  # Inter-|   Receive                                                |  Transmit
  # face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
  #  lo:1123188990665 140494686    0    0    0     0          0         0 1123188990665 140494686    0    0    0     0       0          0
  # eth0:2019336512213 1569379190    0    0    0     0          0       167 1444505341421 1260665633    0    0    0     0       0          0
  # eth1:391147763647 326018981    0    0    0     0          0       176 309762211492 292904289    0    0    0     0       0          0
  # sit0:       0       0    0    0    0     0          0         0        0       0    0    0    0     0       0          0

  NETWORK_INTERFACE = settings.net_io_eth_card

  def process_line(self, line):
    """
    parse a line and return iostats

    returns None if the line is invalid

    @type  line: string
    @param line: the log message
    @rtype     : dictionary
    @return    : the parsed line
    """
    # first separate out the original lines
    parts = line.split(chr(0))

    # sometimes iostat doesn't return anything
    # so ignore these lines
    if len(parts) == 1:
      return None

    # split the line for purposes
    # of getting the hostname and the date
    first_parts = parts[0].split(" ")

    stats = {}

    # get the host and the date
    host = first_parts[0]
    date = first_parts[1] + " " + first_parts[2]

    stats['n_hostname'] = host
    stats['n_date'] = date

    # ignore everything before the line of network information
    parts = parts[4:]
    line_stats = {}
    for line in parts:
      if line == '':
        continue

      # get the network interface
      interface = line.split(":")[0].strip()
      if interface == self.NETWORK_INTERFACE:
        self._handle_line(line, stats)

    return stats

  @staticmethod
  def _handle_line(line, stats):
    """
    Takes a line of ethernet output
    and adds keys and values to the stats
    parameter

    @type  line : string
    @param line : the log message
    @type  stats: dictionary
    @param stats: a parsed dictionary to add to
    """
    cor = {0:  'n_in_bytes',
           1:  'n_in_packets',
           2:  'n_in_errs',
           3:  'n_in_drop',
           4:  'n_in_fifo',
           5:  'n_in_frame',
           6:  'n_in_compressed',
           7:  'n_in_multicast',
           8:  'n_out_bytes',
           9:  'n_out_packets',
           10: 'n_out_errs',
           11: 'n_out_drop',
           12: 'n_out_fifo',
           13: 'n_out_colls',
           14: 'n_out_carrier',
           15: 'n_out_compressed',
           }
    i = 0
    parts = line.split(" ")
    for part in parts:
      if part == '':
        continue

      # special case for the first item in the line.
      # the first item is "NIC:value", so this
      # removes the "NIC:"
      if i == 0:
        stats[cor[i]] = part.split(":")[1]
      else:
        stats[cor[i]] = part
      i += 1

  def store_line(self, stats):
    """
    Given statitistics, store them in to MySQL
    with an ORM mechanism

    @type  stats: dictionary
    @param stats: a dictionary to store
    """
    if stats == None:
      return

    conn = db.connection.connect()

    common.store_orm('net_io', stats, conn)
