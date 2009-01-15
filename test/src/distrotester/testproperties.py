# (c) Copyright 2008 Cloudera, Inc.
#
# @author aaron
#
# This class manages user settings that can be loaded in to the distribution
# testing program for the Cloudera Hadoop distribution

import logging
import os
import sys

from   distrotester.constants import *
import com.cloudera.tools.ec2 as ec2
import com.cloudera.util.output as output
from   com.cloudera.util.argparser import ArgParser
from   com.cloudera.util.properties import Properties


# if we get this symbol on the command line, we stop processing args
stopParsingArgsSymbol = "--"


# well-defined location for primary Properties object
mainProperties = None
def getProperties():
  """ Returns the singleton primary properties object """
  global mainProperties
  return mainProperties

def setProperties(props):
  """ Sets the primary properties object """
  global mainProperties
  mainProperties = props


class TestProperties(Properties):

  # If an argment sets a property, which one is it?
  cmdLineArgMap = {
    TEST_PLATFORM_ARG  : TEST_PLATFORM_KEY,
    LIST_PLATFORMS_ARG : LIST_PLATFORMS_KEY,
    PACKAGE_BUCKET_ARG : PACKAGE_BUCKET_KEY,
    TEST_BINDIR_ARG    : TEST_BINDIR_KEY,

    EC2_HOME_ARG        : ec2.EC2_HOME_PROP,
    EC2_CERT_ARG        : ec2.EC2_CERT_PROP,
    "-C"                : ec2.EC2_CERT_PROP,
    EC2_PRIVATE_KEY_ARG : ec2.EC2_PRIV_KEY_PROP,
    "-K"                : ec2.EC2_PRIV_KEY_PROP,

    SSH_IDENTITY_ARG    : SSH_IDENTITY_KEY,
    "-i"                : SSH_IDENTITY_KEY,

    AWS_ACCESS_KEY_ARG  : AWS_ACCESS_KEY_ID,
    AWS_SECRET_KEY_ARG  : AWS_SECRET_ACCESS_KEY,
    AWS_ACCOUNT_ID_ARG  : AWS_ACCOUNT_ID_KEY,

    SLAVES_FILE_ARG       : SLAVES_FILE_KEY,
    DISTRIB_TARBALL_ARG   : DISTRIB_TARBALL_KEY,
    DISTRIB_S3_ARG        : DISTRIB_S3_KEY,
    INSTALLER_TARBALL_ARG : INSTALLER_TARBALL_KEY,

    UNATTENDED_ARG      : UNATTENDED_KEY,

    SINGLE_TEST_NAME_ARG : SINGLE_TEST_NAME_KEY,

    # Options related to restarting the test harness and debugging
    EXISTING_INSTANCES_ARG : EXISTING_INSTANCES_KEY,
    BYPASS_UPLOAD_ARG      : BYPASS_UPLOAD_KEY,
    BYPASS_SETUP_ARG       : BYPASS_SETUP_KEY,

    # args specific to remotetest
    RUN_TESTS_ARG       : RUN_TESTS_KEY,
    SETUP_ARG           : SETUP_KEY
  }

  # list of boolean flags (a subset of the above map).
  # Non-boolean flags take an argument;
  # these just set the property to 'true'
  booleanFlags = [
    LIST_PLATFORMS_ARG,
    RUN_TESTS_ARG,
    SETUP_ARG,
    BYPASS_UPLOAD_ARG,
    BYPASS_SETUP_ARG,
    UNATTENDED_ARG
  ]

  # these disable boolean flags
  negativeFlags = [
  ]

  # what environment variables map to which properties?
  envVarMap = {
    "EC2_HOME"         : ec2.EC2_HOME_PROP,
    "EC2_PRIVATE_KEY"  : ec2.EC2_PRIV_KEY_PROP,
    "EC2_CERT"         : ec2.EC2_CERT_PROP,

    AWS_ACCESS_KEY_ENV : AWS_ACCESS_KEY_ID,
    AWS_SECRET_KEY_ENV : AWS_SECRET_ACCESS_KEY,
    AWS_ACCOUNT_ID_ENV : AWS_ACCOUNT_ID_KEY
  }

  # when we load the data from a file, which paths should be
  # absolutized relative to the properties file location?
  normalizePaths = [
    ec2.EC2_CERT_PROP,
    ec2.EC2_PRIV_KEY_PROP,
    SSH_IDENTITY_KEY
  ]

  def __init__(self):
    Properties.__init__(self)
    testArgParser = ArgParser(self.cmdLineArgMap, self.booleanFlags, \
        self.negativeFlags, self.envVarMap)
    self.addArgParser(testArgParser)


def loadAllProperties(properties, argv):
  """ Initializes the provided InstallProperties object; loads the
      appropriate properties file (either the default, or one specified
      with -p), as well as any properties set on the command line or from
      the user's environment """

  PropsFileFlag = "-p"
  PropsFileFlagLong = "--properties"

  # first, load initial vals from environment
  properties.loadFromEnvironment()

  # now load in from a props file on top of this. If the user
  # specifies a props file in particular, use that. otherwise
  # use the default props file.
  # Also in this initial scan of the arg list, look for --help.
  propsFileName = None
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
        logging.error("Error: expected properties file name for " + arg)
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
    logging.error("Try " + sys.argv[0] + " --help")
    sys.exit(1)

  # actually load the properties file here now that we know its name
  if propsFileName != None:
    logging.debug("Reading properties file: " + propsFileName)
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
          logging.info("Warning: Could not load default properties file" \
               + propsFileName)
          logging.info(str(ioe))
      else:
        # If the user specified a props file, though, then any IOE is fatal.
        logging.error("Error: Could not load properties file " \
            + propsFileName)
        logging.error(ioe)
        sys.exit(1)

  # finally, read the command line arguments on top of all of this.
  properties.parseArgs(argv)

  output.setupConsole(properties)
  properties.printTable(output.DEBUG)

  return properties



