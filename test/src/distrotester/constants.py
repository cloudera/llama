# (c) Copyright 2008 Cloudera, Inc.
#
# module: distrotester.constants
# Constants used by the distribution testing tool
#

import os

import com.cloudera.util.output as output


# Which test platform to run on (e.g., FC8.i386)
TEST_PLATFORM_KEY = "test.platform"
TEST_PLATFORM_ARG = "--platform"

# If this arg is given, we just print the available test platform list
LIST_PLATFORMS_KEY = "list.platforms"
LIST_PLATFORMS_ARG = "--list-platforms"

# what S3 bucket do we use to retrieve packages from?
PACKAGE_BUCKET_KEY = "package.bucket"
PACKAGE_BUCKET_ARG = "--bucket"
PACKAGE_BUCKET_DEFAULT = "cloudera-dev-server"

# If the user doesn't specify a different log with --log-filename, what to use
DEFAULT_LOG_FILENAME = "testdistrib.log"

# If the user doesn't specify a different verbosity level, crank it all the
# way up to DEBUG. This is a testing tool, after all.
DEFAULT_LOG_VERBOSITY = output.DEBUG

# Where is the distribution to install and test stored?
DISTRIB_TARBALL_ARG = "--distribution"
DISTRIB_TARBALL_KEY = "distribution.tar.file"

# if we're in a multi-host situation, where did the test controller put the
# slaves file?
SLAVES_FILE_ARG = "--slaves-file"
SLAVES_FILE_KEY = "slaves.file"

# Where do we run the testing program from? By default, wherever the
# 'testdistrib' executable is. But you can override it with this.
TEST_BINDIR_KEY = "test.bin.dir"
TEST_BINDIR_ARG = "--test-bindir"

# Arguments and properties for ec2 usage
EC2_HOME_ARG = "--ec2-home"
EC2_CERT_ARG = "--cert"
EC2_PRIVATE_KEY_ARG = "--private-key"

SSH_IDENTITY_KEY = "ssh.identity"
SSH_IDENTITY_ARG = "--identity" # also -i

AWS_ACCESS_KEY_ID  = "aws.access.key"
AWS_ACCESS_KEY_ARG = "--aws-access-key-id"
AWS_ACCESS_KEY_ENV = "AWS_ACCESS_KEY_ID"

AWS_SECRET_ACCESS_KEY = "aws.secret.key"
AWS_SECRET_KEY_ARG    = "--aws-secret-access-key"
AWS_SECRET_KEY_ENV    = "AWS_SECRET_ACCESS_KEY"

AWS_ACCOUNT_ID_KEY = "aws.account.id"
AWS_ACCOUNT_ID_ARG = "--aws-account-id"
AWS_ACCOUNT_ID_ENV = "AWS_ACCOUNT_ID"

# allows a comma,separated,list of instance ids to be used in lieu of
# commissioning new instances (e.g., for debugging the test harness)
EXISTING_INSTANCES_KEY = "ec2.existing.instances"
EXISTING_INSTANCES_ARG = "--instances"

SETUP_ARG = "--setup"
SETUP_KEY = "remote.setup"

RUN_TESTS_ARG = "--test"
RUN_TESTS_KEY = "remote.runtests"


# If we know that the distribution has already been uploaded, don't
# need to wait for it to happen a second time.
BYPASS_UPLOAD_ARG = "--bypass-upload"
BYPASS_UPLOAD_KEY = "bypass.upload"

BYPASS_SETUP_ARG = "--bypass-setup"
BYPASS_SETUP_KEY = "bypass.setup"

# Where are the clouderadev ec2 profiles stored, relative to the bindir?
PROFILE_DIR = "profiles"

# What's the base directory containing the test harness, relative
# to the bindir?
HARNESS_BASE_DIR = "."

# default instance count is 1.
DEFAULT_INSTANCES = 1

SSH_RETRIES  = 1 # Do not actually "retry" ssh'd commands.
SSH_PARALLEL = 5 # Do a command on up to five hosts in parallel

SCP_RETRIES  = 3 # Retry upload up to 3 times
SCP_PARALLEL = 5 # Upload to at most 5 hosts concurrently.

# Wait five minutes for instances to start.
DEFAULT_BOOT_TIMEOUT = 300

# What command do we use to execute the PlatformSetup.setup() command
# on the remote hosts?
REMOTE_SETUP_COMMAND = "python /mnt/distrotest/remotetest " + SETUP_ARG

# What command do we use to launch the test batteries on the remote hosts?
REMOTE_TEST_COMMAND = "python /mnt/distrotest/remotetest " + RUN_TESTS_ARG

#  the remote program will record its output to this log file remotely
# (in addition to printing to its stdout for inclusion in our own log)
REMOTE_LOG_FILENAME = "/mnt/cloudera-tester.log"

# How do we invoke the distribution installer
# TODO (aaron): This should be parameterizable by version number
INSTALLER_COMMAND = "/mnt/cloudera-hadoop-0.1.0/bin/install"

INSTALL_PREFIX = "/mnt/installed-distro"
CONFIG_PREFIX = "/mnt/etc/cloudera"

# where should the installer output its logs to
INSTALLER_LOG_FILE = "/mnt/cloudera-installer.log"

BASE_TMP_DIR = "/mnt/tmp"

DFS_DATA_DIR = os.path.join(BASE_TMP_DIR, "data")
DFS_NAME_DIR = os.path.join(BASE_TMP_DIR, "name")
CHECKPOINT_DIR = os.path.join(BASE_TMP_DIR, "secondary")
HADOOP_TMP_DIR = os.path.join(BASE_TMP_DIR, "hadoop")

# Whom do we run hadoop daemons as?
HADOOP_USER_KEY = "hadoop.user"
HADOOP_USER = "hadoop"

# Whom do we access hadoop as?
CLIENT_USER_KEY = "client.user"
CLIENT_USER = "client"

# Whom we install as. Sometimes, also, access or host daemons.
ROOT_USER = "root"

# Where is java stored on this machine?
JAVA_HOME_KEY = "java.home"


# Where are the hive test files, relative to where the test harness runs?
HIVE_TEST_DIR = "hive-tests"

############################################################
# constants pertaining to making bootstrap installations
############################################################

PACKAGE_TARGET = "/mnt"

S3CMD_URL = "http://s3.amazonaws.com/ServEdge_pub/s3sync/s3sync.tar.gz"
S3CMD_PACKAGE_NAME = "s3sync.tar.gz"



