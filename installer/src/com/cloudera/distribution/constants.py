# (c) Copyright 2008 Cloudera, Inc.
#
# @author aaron
#
# All system-wide constants used in the distribution installer

### Here are the constants for all properties keys we use in the installer ###

# if true, then we never prompt for user input
UNATTEND_INSTALL_KEY   = "install.unattended"

# values for UNATTEND_INSTALL_KEY
INSTALL_UNATTENDED_VAL = True
INSTALL_INTERACTIVE_VAL = False

# we assume there's a user at the controls unless they specifically
# tell us otherwise.
UNATTEND_DEFAULT = False

# if not empty, deploy the distribution to the slaves in this file
INSTALL_SLAVES_KEY = "install.slaves.file"

# select components to install
INSTALL_HADOOP_KEY = "hadoop.install"
INSTALL_HIVE_KEY   = "hive.install"
INSTALL_PIG_KEY    = "pig.install"
INSTALL_SCRIBE_KEY = "scribe.install"

# arguments controlling hadoop-specific installation
HADOOP_MASTER_ADDR_KEY = "hadoop.master.addr"
HADOOP_SLAVES_FILE_KEY = "hadoop.slaves.file"
HADOOP_SITE_FILE_KEY   = "hadoop.site.file"
HADOOP_USER_NAME_KEY   = "hadoop.user.name"

HADOOP_PROFILE_KEY     = "hadoop.profile" # master or slave?

# values for HADOOP_PROFILE_KEY
PROFILE_MASTER_VAL = True
PROFILE_SLAVE_VAL = False

# how do we log into other systems to perform remote setups?
SSH_IDENTITY_KEY = "ssh.identity"
SSH_USER_KEY     = "ssh.user"

# Java-specific options
JAVA_HOME_KEY = "java.home"

# where do we install the distribution to
INSTALL_PREFIX_KEY = "install.prefix"
INSTALL_PREFIX_DEFAULT = "/usr/share/cloudera"

UPLOAD_PREFIX_KEY = "install.upload.prefix"
UPLOAD_PREFIX_DEFAULT = "/tmp/cloudera/"

# if we need to ask the user to do text editing of files, what editor?
EDITOR_KEY = "editor"

# if this is unspecified, run '/usr/bin/vi', since Single Unix Spec
# says it must exist. (Only editor we can bank on)
EDITOR_DEFAULT = "/usr/bin/vi"

# where do we load properties from?
PropsFileFlagLong = "--properties"
PropsFileFlag = "-p"

# If this file is found in the cwd, we load it before parsing arguments
defaultPropertyFileName = "install.properties"


### The following constants are from hadoop itself, relating to its config
### in hadoop-default.xml, hadoop-site.xml, etc.

# basic config
MAPRED_JOB_TRACKER = "mapred.job.tracker"
FS_DEFAULT_NAME = "fs.default.name"

FS_TRASH_INTERVAL = "fs.trash.interval"

HADOOP_TMP_DIR = "hadoop.tmp.dir"
DFS_DATA_DIR = "dfs.data.dir"
DFS_NAME_DIR = "dfs.name.dir"
MAPRED_LOCAL_DIR = "mapred.local.dir"


# advanced config settings below here.

REDUCES_PER_JOB = "mapred.reduce.tasks"

MAPS_PER_NODE = "mapred.tasktracker.map.tasks.maximum"
REDUCES_PER_NODE = "mapred.tasktracker.reduce.tasks.maximum"

MAPRED_CHILD_JAVA_OPTS = "mapred.child.java.opts"
MAPRED_CHILD_ULIMIT = "mapred.child.ulimit"
DFS_DATANODE_THREADS = "dfs.datanode.handler.count"
DFS_DATANODE_RESERVED = "dfs.datanode.du.reserved"
DFS_PERMISSIONS = "dfs.permissions"
DFS_REPLICATION = "dfs.replication"
DFS_BLOCK_SIZE = "dfs.block.size"

DFS_NAMENODE_THREADS = "dfs.namenode.handler.count"
JOBTRACKER_THREADS = "mapred.job.tracker.handler.count"

IO_SORT_FACTOR = "io.sort.factor"
IO_SORT_MB = "io.sort.mb"
IO_FILEBUF_SIZE = "io.file.buffer.size"

MAPRED_PARALLEL_COPIES = "mapred.reduce.parallel.copies"
TASKTRACKER_HTTP_THREADS = "tasktracker.http.threads"

MAPRED_SPECULATIVE_MAP =  "mapred.map.tasks.speculative.execution"
MAPRED_SPECULATIVE_REDUCE = "mapred.reduce.tasks.speculative.execution"

MAPRED_SUBMIT_REPLICATION = "mapred.submit.replication"

# where does the 2nn's data go
NN2_CHECKPOINT_DIR = "fs.checkpoint.dir"

MAPRED_SYSTEM_DIR = "mapred.system.dir"
MAPRED_TEMP_DIR = "mapred.temp.dir"


# autoconfigure these for the user
DFS_HOSTS_FILE = "dfs.hosts"
DFS_EXCLUDE_FILE = "dfs.hosts.exclude"

# This is a list of properties we configure
# that should be marked "final" in hadoop-site
finalHadoopProperties = [
  FS_TRASH_INTERVAL,
  HADOOP_TMP_DIR,
  DFS_DATA_DIR,
  DFS_NAME_DIR,
  MAPRED_LOCAL_DIR,
  MAPS_PER_NODE,
  REDUCES_PER_NODE,
  MAPRED_CHILD_ULIMIT,
  DFS_DATANODE_THREADS,
  DFS_DATANODE_RESERVED,
  DFS_PERMISSIONS,
  DFS_BLOCK_SIZE,
  DFS_NAMENODE_THREADS,
  JOBTRACKER_THREADS,
  TASKTRACKER_HTTP_THREADS,
  NN2_CHECKPOINT_DIR,
  MAPRED_TEMP_DIR,
  DFS_HOSTS_FILE,
  DFS_EXCLUDE_FILE ]


# some default values we suggest for them
DEFAULT_JT_PORT = 9001
DEFAULT_NN_PORT = 9000

ONE_DAY_IN_MINUTES = 24 * 60

DEFAULT_TRASH_INTERVAL = ONE_DAY_IN_MINUTES

HADOOP_TMP_DEFAULT = "/tmp/hadoop"
HDFS_DATA_DIR_DEFAULT = "/home/hadoop/hdfs/data"
HDFS_NAME_DIR_DEFAULT = "/home/hadoop/hdfs/name"
HDFS_2NN_DIR_DEFAULT  = "/home/hadoop/hdfs/secondary"

DEFAULT_RAM_GUESS = 2048
DEFAULT_CORES_GUESS = 2

# MapReduce is likely to pitch a fit with less than this much heap
MIN_CHILD_HEAP = 200

# This is Hadoop's default. Let's leave it here for now.
DEFAULT_DATANODE_THREADS = 3

ONE_GB_IN_BYTES = 1024 * 1024 * 1024
DEFAULT_RESERVED_DU = ONE_GB_IN_BYTES

# 128 MB default blocks
DEFAULT_BLOCK_SIZE = 128 * 1024 * 1024

# This is hadoop's default maximum hdfs replication factor.
DFS_MAX_REP = 512

DEFAULT_REPLICATION = 3

# Minimum threads we recommend a NN/JT to have
MIN_DAEMON_THREADS = 5

# Maximum threads we recommend a NN/JT to have
MAX_DAEMON_THREADS = 128

# io.file.buffer.size should be much bigger than 4K
DEFAULT_FILEBUF_SIZE = 65536

# minimum ulimit we will allow for a child is 512 MB
# to ensure that Java definitely has room to launch
MIN_ULIMIT = 512 * 1024

# mapred.parallel.copies recommendation bounds
MIN_PARALLEL_COPIES = 5
MAX_PARALLEL_COPIES = 100

DEFAULT_SPECULATIVE_MAP = True
DEFAULT_SPECULATIVE_REDUCE = False

# This is Hadoop's default submit replication.
# We use it as a minimum, and take Hadoop's recommendation to use
# sqrt(|nodes|), but only if it's > 10
MIN_SUBMIT_REPLICATION = 10

# never allow replication factors higher than the DFS maximum, or
# Hadoop will throw errors on job creation.
MAX_SUBMIT_REPLICATION = DFS_MAX_REP

DEFAULT_MAPRED_SYS_DIR = "/hadoop/system/mapred"

# underneath of $prefix, where do the actual installs of different
# programs get put?
APP_SUBDIR = "apps"

# Underneath of $prefix/apps/, where do all the individual programs go?
# TODO: We should really have some way of embedding this in the build
# process so that we don't have to manually change this every time.
HADOOP_VERSION = "0.18.2"
HADOOP_INSTALL_SUBDIR = "hadoop-" + HADOOP_VERSION

# What subdir of the installation system (Relative to the 'install' program)
# holds all the packages?
PACKAGE_PATH = "../packages/"

HADOOP_PACKAGE = "hadoop-" + HADOOP_VERSION + ".tar.gz"

