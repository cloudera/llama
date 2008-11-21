#!/usr/bin/python
# (c) Copyright 2008 Cloudera, Inc.
#
# @author aaron
#
# This class manages user settings that can be loaded in from the
# ~/.cloudera/dev_properties file, the environment, and/or the command
# line arguments

import os
import sys

import com.cloudera.util.output as output
from com.cloudera.util.argparser import ArgParser
from com.cloudera.util.properties import Properties

# where do we load properties from?
defaultPropertyFileName = os.getenv("HOME",".") + "/.cloudera/dev.properties"
PropsFileFlagLong = "--properties"
PropsFileFlag = "-p"

# what separates these properties from the next argument parser?
stopParsingArgsSymbol = "--"

class DevProperties(Properties):

  # If an argment sets a property, which one is it?
  cmdLineArgMap = {
    "--identity"    : "ssh.identity",
    "-i"            : "ssh.identity",
    "--keypair"     : "ssh.keypair.name",
    "-k"            : "ssh.keypair.name",
    "--ami"         : "ec2.default.ami",
    "--ec2-home"    : "ec2.home",
    "--private-key" : "ec2.private.key",
    "-K"            : "ec2.private.key",
    "--cert"        : "ec2.cert",
    "-C"            : "ec2.cert",
    "--username"    : "ssh.user",
    "-u"            : "ssh.user",
    "--group"       : "ec2.group",
    "-g"            : "ec2.group",
    "--create-group" : "ec2.group.force",
    "--no-create-group" : "ec2.group.force",
    "-G"            : "ec2.group.force",
    "--instance-count" : "ec2.instance.count",
    "-n"            : "ec2.instance.count",
    "--instance-type" : "ec2.instance.type",
    "-t"            : "ec2.instance.type",
    "--availability-zone"  : "ec2.availability.zone",
    "-z"            : "ec2.availability.zone",
    "--connection-timeout" : "ec2.connection.timeout",
    "--request-timeout"    : "ec2.request.timeout",
    "--boot-timeout"       : "ec2.boot.timeout",
    "--attach-timeout"     : "ec2.attach.timeout",
    "--login"       : "ec2.auto.login",
    "--no-login"    : "ec2.auto.login",
    "--ebstab"      : "ec2.ebstab",
    "-b"            : "ec2.ebstab",
    "--init-script" : "ec2.local.init.script",
    "-s"            : "ec2.local.init.script",
    "--verbose"     : "output.verbose",
    "-v"            : "output.verbose",
    "--quiet"       : "output.quiet",
    "-q"            : "output.quiet",
    "--debug"       : "output.debug",
    "--test"        : "ec2.use.test.amis",
    "-I"            : "ec2.instance.id",
    "--instance"    : "ec2.instance.id"
  }

  # list of boolean flags (a subset of the above map).
  # Non-boolean flags take an argument;
  # these just set the property to 'true'
  booleanFlags = [
    "--login",
    "--create-group",
    "-G",
    "--verbose",
    "-v",
    "--quiet",
    "-q",
    "--debug",
    "--test"
  ]

  # these disable boolean flags
  negativeFlags = [
    "--no-login",
    "--no-create-group"
  ]

  # what environment variables map to which properties?
  envVarMap = {
    "CLOUDERA_SSH_IDENTITY" : "ssh.identity",
    "CLOUDERA_KEYPAIR_NAME" : "ssh.keypair.name",
    "CLOUDERA_SSH_USER"     : "ssh.user",
    "CLOUDERA_DEFAULT_AMI"  : "ec2.default.ami",
    "EC2_HOME"              : "ec2.home",
    "EC2_PRIVATE_KEY"       : "ec2.private.key",
    "EC2_CERT"              : "ec2.cert",
  }

  # when we load the data from a file, which paths should be
  # absolutized relative to the properties file location?
  normalizePaths = [
    "ssh.identity",
    "ec2.private.key",
    "ec2.cert",
    "ec2.ebstab",
    "ec2.local.init.script"
  ]

  def __init__(self):
    Properties.__init__(self)
    devPropsArgParser = ArgParser(self.cmdLineArgMap, self.booleanFlags, \
        self.negativeFlags, self.envVarMap)
    devPropsArgParser.setStopSymbol(stopParsingArgsSymbol)
    self.addArgParser(devPropsArgParser)


def loadAllProperties(properties, argv):
  """ Initializes the provided DevProperties object; loads the
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
      print "Warning: Could not load default properties file" , propsFileName
    else:
      output.printlnError("Error: Could not load properties file " \
          + propsFileName)
      output.printlnError(ioe)
      sys.exit(1)

  # finally, read the command line arguments on top of all of this.
  properties.parseArgs(argv)

  properties.printTable(output.DEBUG)

  return properties



