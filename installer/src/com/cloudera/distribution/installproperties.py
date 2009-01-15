# (c) Copyright 2009 Cloudera, Inc.
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
#
# @author aaron
#
# This class manages user settings that can be loaded in to the 'install'
# program for the Cloudera Hadoop distribution

import os
import sys

from   com.cloudera.distribution.constants import *
import com.cloudera.util.output as output
from   com.cloudera.util.argparser import ArgParser
from   com.cloudera.util.properties import Properties


# if we get this symbol on the command line, we stop processing args
stopParsingArgsSymbol = "--"

class InstallProperties(Properties):

  # If an argment sets a property, which one is it?
  cmdLineArgMap = {
    "--unattend"       : UNATTEND_INSTALL_KEY,
    "--interactive"    : UNATTEND_INSTALL_KEY,
    "--deploy-slaves"  : INSTALL_SLAVES_KEY,

    "--install-hadoop" : INSTALL_HADOOP_KEY,
    "--without-hadoop" : INSTALL_HADOOP_KEY,
    "--hadoop-master"  : HADOOP_MASTER_ADDR_KEY,
    "--hadoop-slaves"  : HADOOP_SLAVES_FILE_KEY, # master-only
    "--hadoop-site"    : HADOOP_SITE_FILE_KEY,
    "--hadoop-user"    : HADOOP_USER_NAME_KEY,
    "--as-master"      : HADOOP_PROFILE_KEY,
    "--as-slave"       : HADOOP_PROFILE_KEY,

    "--install-hive"   : INSTALL_HIVE_KEY,
    "--without-hive"   : INSTALL_HIVE_KEY,

    "--install-pig"    : INSTALL_PIG_KEY,
    "--without-pig"    : INSTALL_PIG_KEY,

    "--install-scribe" : INSTALL_SCRIBE_KEY,
    "--without-scribe" : INSTALL_SCRIBE_KEY,

    "--overwrite-htdocs": OVERWRITE_HTDOCS_KEY,

    "--java-home"      : JAVA_HOME_KEY,

    "--prefix"         : INSTALL_PREFIX_KEY,
    "--config-prefix"  : CONFIG_DIR_KEY,

    # if true, create missing ~hadoop/.ssh/id_rsa
    "--create-keys"    : CREATE_SSHKEYS_KEY,
    # filename of the remote public key file
    "--hadoop-pubkey"  : HADOOP_PUBKEY_KEY,

    # If true, refuses to start Hadoop, HDFS, etc.
    "--no-start-daemons" : NO_DAEMONS_KEY,
    "--start-daemons"    : NO_DAEMONS_KEY,

    # options used only for debugging
    "--test-mode"      : TEST_MODE_KEY,
    "--remote-prefix"  : REMOTE_PREFIX_KEY,
    "--install-bindir" : INSTALL_BINDIR_KEY,

    # Options used to tell apps (pig, hive, etc) how to connect
    # to the main daemons.
    # These are used in conjunction with a pre-supplied hadoop-site,
    # when we can't poll the user for these values directly.
    "--jobtracker"     : JOB_TRACKER_KEY,
    "--namenode"       : NAMENODE_KEY,
    "--scribe-master"  : SCRIBE_MASTER_ADDR_KEY,

    "--scribe-log-dir" : SCRIBE_LOG_DIR_KEY,

    # the following settings apply to master only

    "--make-dfs-hosts"    : MAKE_DFS_HOSTS_KEY,
    "--make-dfs-excludes" : MAKE_DFS_EXCLUDES_KEY,

    "--format-hdfs"    : FORMAT_DFS_KEY,
    "--no-format-hdfs" : FORMAT_DFS_KEY,

    "--editor"         : EDITOR_KEY,

    "--upload-prefix"  : UPLOAD_PREFIX_KEY,

    "--identity"       : SSH_IDENTITY_KEY,
    "-i"               : SSH_IDENTITY_KEY,
    "-u"               : SSH_USER_KEY,
    "--user"           : SSH_USER_KEY,

    # db super user stuff
    "--db-superuser"   : DB_SUPERUSER_PASSWD_KEY,
  }

  # list of boolean flags (a subset of the above map).
  # Non-boolean flags take an argument;
  # these just set the property to 'true'
  booleanFlags = [
    "--as-slave",
    "--install-hadoop",
    "--install-hive",
    "--install-pig",
    "--install-scribe",
    "--overwrite-htdocs",
    "--format-hdfs",
    "--unattend",
    "--test-mode",
    "--no-start-daemons",
    "--create-keys"
  ]

  # these disable boolean flags
  negativeFlags = [
    "--as-master",
    "--without-hadoop",
    "--without-hive",
    "--without-pig",
    "--without-scribe",
    "--no-format-hdfs",
    "--interactive",
    "--start-daemons"
  ]

  # what environment variables map to which properties?
  envVarMap = {
    "JAVA_HOME" : JAVA_HOME_KEY,
    "EDITOR"    : EDITOR_KEY,

    HADOOP_PID_DIR_ENV : HADOOP_PID_DIR_KEY
  }

  # when we load the data from a file, which paths should be
  # absolutized relative to the properties file location?
  normalizePaths = [
  ]

  def __init__(self):
    Properties.__init__(self)
    installArgParser = ArgParser(self.cmdLineArgMap, self.booleanFlags, \
        self.negativeFlags, self.envVarMap)
    self.addArgParser(installArgParser)


def loadAllProperties(properties, argv):
  """ Initializes the provided InstallProperties object; loads the
      appropriate properties file (either the default, or one specified
      with -p), as well as any properties set on the command line or from
      the user's environment """

  # first, load initial vals from environment
  properties.loadFromEnvironment()

  # now load in from a props file on top of this. If the user
  # specifies a props file in particular, use that. otherwise
  # use the default props file.
  # Also in this initial scan of the arg list, look for --help.
  propsFileName = defaultPropertyFileName
  useDefaultFile = True
  i = 0
  while i < len(argv):
    arg = argv[i]
    if arg == stopParsingArgsSymbol:
      # don't parse args past this symbol
      break
    elif arg == PropsFileFlag or arg == PropsFileFlagLong:
      useDefaultFile = False
      i = i + 1
      try:
        propsFileName = argv[i]
      except IndexError:
        output.printlnError("Error: expected properties file name for " + arg)
        sys.exit(1)
    elif arg == "--help":
      print "usage: " + sys.argv[0] + " [options]"
      print "Supported options:"
      print "-p    Set properties file to load"
      print ""
      properties.printUsage()
      sys.exit(1)

    # end argv loop
    i = i + 1

  # check that all the cmd line arguments are handled by an arg parser

  # first, remove PropsFileFlag(Long); it's known that the argparsers
  # won't handle that.
  propsFlagFound = True
  while propsFlagFound:
    try:
      # remove this index
      idx = argv.index(PropsFileFlag)
      newArgv = []
      newArgv.extend(argv[0:idx])
      newArgv.extend(argv[idx + 2:])
      argv = newArgv
    except ValueError:
      propsFlagFound = False

  propsFlagFound = True
  while propsFlagFound:
    try:
      # remove this index
      idx = argv.index(PropsFileFlagLong)
      newArgv = []
      newArgv.extend(argv[0:idx])
      newArgv.extend(argv[idx + 2:])
      argv = newArgv
    except ValueError:
      propsFlagFound = False


  # now actually check that we use all the flags
  if not properties.usesAllFlags(argv):
    output.printlnError("Try " + sys.argv[0] + " --help")
    sys.exit(1)

  # actually load the properties file here now that we know its name
  output.printlnDebug("Reading properties file: " + propsFileName)
  try:
    # Relative paths loaded here must be normalized to be relative
    # to dirname(propsfilename)
    propsDir = os.path.dirname(os.path.realpath(propsFileName))
    handle = open(propsFileName)
    properties.load(handle, propsDir, properties.normalizePaths)
    handle.close()
  except IOError, ioe:
    if useDefaultFile:
      # if the default file can't be found, we just silently ignore that..
      if os.path.exists(propsFileName):
        output.printlnInfo("Warning: Could not load default properties file" \
             + propsFileName)
        output.printlnInfo(str(ioe))
    else:
      # If the user specified a props file, though, then any IOE is fatal.
      output.printlnError("Error: Could not load properties file " \
          + propsFileName)
      output.printlnError(ioe)
      sys.exit(1)

  # finally, read the command line arguments on top of all of this.
  properties.parseArgs(argv)

  output.setupConsole(properties)
  properties.printTable(output.DEBUG)

  return properties



