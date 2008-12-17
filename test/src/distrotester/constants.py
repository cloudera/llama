# (c) Copyright 2008 Cloudera, Inc.
#
# module: distrotester.constants
# Constants used by the distribution testing tool
#

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

# Where are the clouderadev ec2 profiles stored, relative to the bindir?
PROFILE_DIR = "../profiles"

# default instance count is 1.
DEFAULT_INSTANCES = 1
