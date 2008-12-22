# (c) Copyright 2008 Cloudera, Inc.
"""
General settings for log movers
"""
import os

# the path to $HADOOP_HOME
hadoop_home = '/usr/local/hadoop'
# the path to the Hadoop binary
bin_path = os.path.join(hadoop_home, "bin/hadoop")
# the HDFS command to ls
ls_command = "fs -ls"
# the HDFS command to mkdir
mkdir_command = "fs -mkdir"
# the HDFS command to copy
copy_command = "fs -copyFromLocal"

# $GIT_ROOT
git_root = "/home/alex/git"

# log output dir
log_out = os.path.join(git_root, "projects/HadoopDist/log_mover/src/logs")

# the different movers that exist
movers = [#'vmstat',
          #'iostat',
          #'net_io',
          'hadoop', #by default we should only be looking at hadoop logs
          #'mpstat',
         ]

# the location of logs for each mover
log_locations = {'vmstat': '/var/log/scribe/central/vmstat',
                 'iostat': '/var/log/scribe/central/iostat',
                 'net_io': '/var/log/scribe/central/net-io',
                 'hadoop': '/var/log/scribe/central/hadoop',
                 'mpstat': '/var/log/scribe/central/mpstat',
                }


# the prefixes for scribe logs
# only Scribe-collected logs need to be here
log_prefixes = {'vmstat': 'vmstat_',
                'iostat': 'iostat_',
                'net_io': 'net-io_',
                'hadoop': 'hadoop_',
                'mpstat': 'mpstat_',
               }

# the time in which logs should remain
# in the db for each mover (in seconds)
#
# -1 to not prune
#
# NOTE: making 'hadoop' prune will only
# partially prune hadoop logs.  Now that
# the hadoop_exras table has been introduced,
# the general pruner will not prune that
# table
log_lifetime = {'vmstat': 60 * 5,
                'iostat': 60 * 5,
                'net_io': 60 * 5,
                'hadoop': 60 * 60 * 24 * 90, # 3 months
                'mpstat': 60 * 5,
               }

# the MySQL column name to compare
# old logs against when pruning from
# MySQL
log_prune_date_col = {'vmstat': 'v_date',
                      'iostat': 'io_date',
                      'net_io': 'n_date',
                      'hadoop': 'h_date',
                      'mpstat': 'm_date',
                     }

# DB connection information
db_host = 'localhost'
db_user = 'ana'
db_password = 'ana'
db_database = 'ana'

# the size of the input buffer when
# reading in a scribe log
scribe_buffer_size = 1024

# the ethernet card to monitor
# when looking at /proc/net/dev
# output
net_io_eth_card = "eth0"