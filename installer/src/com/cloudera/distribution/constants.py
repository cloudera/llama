# (c) Copyright 2008 Cloudera, Inc.
#
# @author aaron
#
# All system-wide constants used in the distribution installer

import os

#######################################################################
### Here are the constants for all properties keys we use in the installer ###

# if true, then we never prompt for user input
UNATTEND_INSTALL_KEY   = "install.unattended"

# values for UNATTEND_INSTALL_KEY
INSTALL_UNATTENDED_VAL = True
INSTALL_INTERACTIVE_VAL = False

# we assume there's a user at the controls unless they specifically
# tell us otherwise.
UNATTEND_DEFAULT = False

# by default, if localhost is in the slaves file, the installer will
# not deploy to this host. Hadoop's start/stop scripts also depend on
# Hadoop being installed on the same path on all machines. But for
# testing purposes, we want to be able to override both of these.
TEST_MODE_KEY = "test.mode"
TEST_MODE_DEFAULT = False

# this is set in global prereq config phase; this is ordinarily set to
# the same as install.prefix, unless test.mode is True
REMOTE_PREFIX_KEY = "remote.prefix"

# if not empty, deploy the distribution to the slaves in this file
INSTALL_SLAVES_KEY = "install.slaves.file"

# select components to install
INSTALL_HADOOP_KEY = "hadoop.install"
INSTALL_HIVE_KEY   = "hive.install"
INSTALL_PIG_KEY    = "pig.install"
INSTALL_SCRIBE_KEY = "scribe.install"

# by default, we install all packages. Doing this just for good practice
# in case we include some "optional" packages later.
INSTALL_HADOOP_DEFAULT = True
INSTALL_HIVE_DEFAULT   = True
INSTALL_PIG_DEFAULT    = True
INSTALL_SCRIBE_DEFAULT = True

# arguments controlling hadoop-specific installation
HADOOP_MASTER_ADDR_KEY = "hadoop.master.addr"
HADOOP_SLAVES_FILE_KEY = "hadoop.slaves.file"
HADOOP_SITE_FILE_KEY   = "hadoop.site.file"
HADOOP_USER_NAME_KEY   = "hadoop.user.name"

# what username do we run hadoop as?
HADOOP_USER_NAME_DEFAULT = "hadoop"

HADOOP_PROFILE_KEY     = "hadoop.profile" # master or slave?

# values for HADOOP_PROFILE_KEY
PROFILE_MASTER_VAL = False
PROFILE_SLAVE_VAL = True

# how do we log into other systems to perform remote setups?
SSH_IDENTITY_KEY = "ssh.identity"
SSH_USER_KEY     = "ssh.user"

JOB_TRACKER_KEY = "job.tracker"
NAMENODE_KEY     = "namenode"

# which JT do we connect pig jobs to?
PIG_JOBTRACKER_KEY = JOB_TRACKER_KEY

# which HDFS does Hive's metastore live in
HIVE_NAMENODE_KEY = NAMENODE_KEY

# Java-specific options
JAVA_HOME_KEY = "java.home"

# where do we install the distribution to
INSTALL_PREFIX_KEY = "install.prefix"
INSTALL_PREFIX_DEFAULT = "/usr/share/cloudera"

UPLOAD_PREFIX_KEY = "install.upload.prefix"
UPLOAD_PREFIX_DEFAULT = "/tmp/cloudera/"

# it is important in the general case that this be the same as the local
# install prefix, but for testing purposes we will want to be able to override
REMOTE_PREFIX_KEY = "remote.prefix"
REMOTE_PREFIX_DEFAULT = INSTALL_PREFIX_DEFAULT

# do we format hdfs?
FORMAT_DFS_KEY = "hadoop.hdfs.format"
FORMAT_DFS_DEFAULT = False

# where do we set up the 'etc' dir ?
CONFIG_DIR_KEY = "cloudera.etc.dir"
CONFIG_DIR_DEFAULT = "/etc/cloudera"

# if True, we allow enabling of native compression
ALLOW_NATIVE_COMPRESSION_KEY = "mapred.compression.allow"
ALLOW_NATIVE_COMPRESSION_DEFAULT = True

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


#######################################################################
### Some more constants affecting the installer system itself

# TODO (aaron): 0.2 - Allow the user to change these settings

# how many tries do we make when uploading to any given host
NUM_SCP_RETRIES = 3

# How many hosts do we access at the same time when doing scpall's
NUM_SCP_PARALLEL_THREADS = 8

# since ssh returns the exit status of the underlying program too,
# we actually don't perform retries of ssh commands. If the command
# fails the first time, that's all there is to it.
NUM_SSH_RETRIES = 1

# How many hosts do we access at the same time when doing sshall's?
NUM_SSH_PARALLEL_THREADS = 16

# Wait at most 300 seconds (5 minutes) for safe mode to exit.
MAX_SAFE_MODE_WAIT_TIME = 300

#######################################################################
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


#######################################################################
# Hive configuration

HIVE_WAREHOUSE_DIR = "/user/hive/warehouse"


#######################################################################
# The following section deals with paths associated with the layout of
# the packages within the installer itself and their destinations relative
# to the install prefix

# Given the path to the 'installer' program via sys.argv[0], what path,
# when joined with dirname(installer) gives us the base of the installation
# system?
DISTRIB_BASE_PATH = ".."

# What subdir of the installation system (Relative to the 'install' program)
# holds all the packages?
PACKAGE_PATH = os.path.join(DISTRIB_BASE_PATH, "packages/")

# relative to the distribution base path, where is the installer program?
INSTALLER_SUBDIR = "bin"

# underneath of $prefix, where do the actual installs of different
# programs get put?
APP_SUBDIR = "apps"

# Underneath of $prefix/apps/, where do all the individual programs go?
# TODO (aaron): 0.2 Need some way of embedding this in the build process
# so that we don't have to manually change this every time.
HADOOP_VERSION = "0.18.2"
HADOOP_INSTALL_SUBDIR = "hadoop-" + HADOOP_VERSION
HADOOP_PACKAGE = "hadoop-" + HADOOP_VERSION + ".tar.gz"

# Hive and Pig don't have formal releases; we track their svn repository
# version numbers from when we froze their commits.

HIVE_VERSION = "r725920"
HIVE_INSTALL_SUBDIR = "hive-" + HIVE_VERSION
HIVE_PACKAGE = "hive-" + HIVE_VERSION + ".tar.gz"

PIG_VERSION = "0.1.1"
PIG_INSTALL_SUBDIR = "pig-" + PIG_VERSION
PIG_PACKAGE = "pig-" + PIG_VERSION + ".tar.gz"


