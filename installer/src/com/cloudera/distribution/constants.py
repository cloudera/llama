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
HADOOP_MASTER_FILE_KEY = "hadoop.master.file"
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

# if we need to ask the user to do text editing of files, what editor?
EDITOR_KEY = "editor"

# where do we load properties from?
PropsFileFlagLong = "--properties"
PropsFileFlag = "-p"

# If this file is found in the cwd, we load it before parsing arguments
defaultPropertyFileName = "install.properties"


