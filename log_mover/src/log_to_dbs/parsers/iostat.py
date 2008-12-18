# (c) Copyright 2008 Cloudera, Inc.
# storage imports
import db.connection

import common

class IOStatParser:
  """
  A parser to parse 'iostat' collected by
  Scribe.  iostat returns data for each
  hard drisk, and this parser averages
  all statistics across all disks
  """

  # sample output directory from the command line):
  # Linux 2.6.18-92.el5 (server10.cloudera.com)         12/01/2008
  #
  # avg-cpu:  %user   %nice %system %iowait  %steal   %idle
  #         1.54    0.00    0.25    0.44    0.00   97.76
  #
  # Device:            tps   Blk_read/s   Blk_wrtn/s   Blk_read   Blk_wrtn
  # sda               8.68       640.11       857.38 1737029519 2326630250
  # sdb               3.83       635.01       717.03 1723196780 1945763408
  # sdc               3.89       629.90       692.35 1709343380 1878801904
  # sdd               4.01       636.18       732.13 1726365756 1986761640

  def process_line(self, line):
    """
    parse a line and return iostats

    returns None if the line is invalid

    @type  line: string
    @param line: the log message
    @rtype     : dictionary
    @return    : a parsed dictionary
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

    stats['io_hostname'] = host
    stats['io_date'] = date

    # manually grab the cpu info
    # everything before index '3' is useless
    self._handle_cpu_info(stats, parts[6].split(" "))

    # remove everything before index 12, which
    # at this point is all junk.  Everything
    # after 12 is good
    parts = parts[12:]

    # note: averages the disk usage across
    # all disks
    total_disk_stats = {}
    disk_num = 0
    first = True
    for part in parts:
      if part == '':
        continue

      # get stats for this disk
      disk_stats = self._handle_disk(part)
      disk_num += 1

      # compute an average
      for disk_stat in disk_stats:
        if first:
          total_disk_stats[disk_stat] = \
                      float(disk_stats[disk_stat])
        else:
          total_disk_stats[disk_stat] += \
                      float(disk_stats[disk_stat])
      first = False

    for disk_stat in total_disk_stats:
      stats[disk_stat] = total_disk_stats[disk_stat] / disk_num

    return stats

  @staticmethod
  def _handle_disk(disk_line):
    """
    Parses a disk line

    @type  line: string
    @param line: a line that has statistics about
                 a specific disk
    @rtype     : dictionary
    @return    : a parsed dictionary
    """
    cor = {0: 'io_disk_tps', #     'tps'
           1: 'io_disk_read_s', #  'Blk_read/s'
           2: 'io_disk_write_s', # 'Blk_wrtn/s'
           3: 'io_disk_read', #    'Blk_read'
           4: 'io_disk_write', #   'Blk_wrtn
           }
    i = 0
    disk_stats = {}
    parts = disk_line.split(" ")
    start = True
    for part in parts:
      if start:
        start = False
        continue
      elif part == '':
        continue

      disk_stats[cor[i]] = part
      i += 1

    return disk_stats

  @staticmethod
  def _handle_cpu_info(stats, cpu_arr):
    """
    Parses the cpu array from iostat and
    adds new keys and values to the stats dict

    @type  stats  : dictionary
    @param stats  : the statistics dictionary to add to
    @type  cpu_arr: array
    @param cpu_arr: the array of CPU info
    """
    # start at 1 instead of 0 to skip the
    # hard drive name
    cor = {1: 'io_cpu_user',
           2: 'io_cpu_nice',
           3: 'io_cpu_system',
           4: 'io_cpu_iowait',
           5: 'io_cpu_steal',
           6: 'io_cpu_idle',
           }
    i = 0
    for part in cpu_arr:
      if part == '':
        continue

      if i != 0:
        stats[cor[i]] = part
      i += 1

  def store_line(self, stats):
    """
    Given statitistics, store them in to MySQL
    with an ORM mechanism

    @type  stats: dictionary
    @param stats: the dictionary with a parsed line
    """
    if stats == None:
      return

    conn = db.connection.connect()

    common.store_orm('iostat', stats, conn)