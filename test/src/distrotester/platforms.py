# (c) Copyright 2009 Cloudera, Inc.
#
# module: distrotester.platforms
#
# Represents platform-specific launch and installation capabilities.
# Here is where we control what EC2 image we launch for, and where we
# select the proper module to use to run the system setup procedures.

import logging
import os
import sys
import unittest

import com.cloudera.tools.ec2 as ec2
import com.cloudera.tools.shell as shell
from   com.cloudera.util.properties import Properties

from   distrotester.setup.fedora8 import Fedora8Setup
from   distrotester.constants import *
from   distrotester.testerror import TestError
from   distrotester.installtests.standalone import StandaloneTest
from   distrotester.installtests.multihost import MultiHostTest


def listPlatforms():
  """ Lists the platforms available to select for testing on. """

  logging.info("Available platforms (select with " \
      + TEST_PLATFORM_ARG + ")")

  profileDirEntries = os.listdir(PROFILE_DIR)
  for entry in profileDirEntries:
    if entry.endswith(".properties"):
      # This is a $profileName.properties file that we can use
      platform = entry[0:len(entry) - 11]
      logging.info("  " + platform)


def profileForPlatform(platformName):
  """ Return the .properties profile to use to launch a given platform """

  profileFileName = os.path.join(PROFILE_DIR, platformName + ".properties")
  if not os.path.exists(profileFileName):
    raise TestError("No such platform: " + platformName)

  return profileFileName


def setupForPlatform(platformName, properties):
  """ Return a PlatformSetup object specific to the intended platform.
      This is a factory method from string -> PlatformSetup """

  # TODO(aaron): New platform? Add it to this list.
  if platformName == "fc8.i386":
    return Fedora8Setup("i386", properties)
  elif platformName == "fc8.x86_64":
    return Fedora8Setup("x86_64", properties)
  elif platformName == "fc8.multi.i386":
    return Fedora8Setup("i386", properties)
  elif platformName == "fc8.multi.x86_64":
    return Fedora8Setup("x86_64", properties)
  else:
    raise TestError("No Setup object available for platform: " + platformName)


def testSuiteForPlatform(platformName, properties):
  """ return a pyunit TestSuite for execution on the remote platform """

  # If the user has specified a particular test name to run, then run only
  # that one. Otherwise, make suites with "test" as the prefix, which selects
  # all tests.
  testNamePrefix = properties.getProperty(SINGLE_TEST_NAME_KEY, "test")

  # TODO(aaron): New platform? Add it to this list.
  if platformName == "fc8.i386":
    return unittest.makeSuite(StandaloneTest, testNamePrefix)
  elif platformName == "fc8.x86_64":
    return unittest.makeSuite(StandaloneTest, testNamePrefix)
  elif platformName == "fc8.multi.i386":
    return unittest.makeSuite(MultiHostTest, testNamePrefix)
  elif platformName == "fc8.multi.x86_64":
    return unittest.makeSuite(MultiHostTest, testNamePrefix)
  else:
    raise TestError("No test suite available for platform: " + platformName)


def launchInstances(platformName, properties, configOnly=False):
  """ Launch one or more EC2 instances and return a list of instance ids.
      The instance launch process is governed by loading in the dev.properties
      file associated with platformName. This is loaded "underneath" the
      primary properties object -- anything that the user set via an external
      properties file, command line switches, etc, are preserved, but this
      sets new properties that the user left unset.

      If configOnly is true, loads all the config for the platform,
      but doesn't actually launch the instances. Just set up params as if
      we did.
  """

  # This method both sets the config properties, and then
  # also creates the instances (if configOnly is False).
  # TODO(aaron): Refactor into two different methods. (CH-77)

  profileFilename = profileForPlatform(platformName)
  profileProps = Properties()

  # load the properties in for the instance
  handle = open(profileFilename)
  profileProps.load(handle)
  handle.close()

  # overwrite the contents of 'properties' on top of this; any externally
  # configured properties take priority
  userKeys = properties.keys()
  for key in userKeys:
    profileProps.setProperty(key, properties.getProperty(key))

  # Now copy the merged results back.
  allKeys = profileProps.keys()
  for key in allKeys:
    properties.setProperty(key, profileProps.getProperty(key))

  # now determine the args to use when creating the instances

  ami = profileProps.getProperty(ec2.EC2_AMI_PROP)
  instanceType = profileProps.getProperty(ec2.EC2_INSTANCE_TYPE_PROP)
  arch = ec2.getArchForInstanceType(instanceType)
  instanceCount = properties.getInt(ec2.EC2_INSTANCES_PROP, DEFAULT_INSTANCES)
  group = ec2.getEc2SecurityGroup(profileProps)
  keyPair = profileProps.getProperty(ec2.EC2_KEYPAIR_PROP)
  userData = None
  zone = None

  if instanceType == None:
    raise TestError("No instance type set for " + platformName)
  if arch == None:
    raise TestError("No arch for instance type in " + platformName)
  if ami == None:
    raise TestError("AMI not set in " + platformName)
  if group == None:
    raise TestError("No group set for " + platformName)
  if keyPair == None:
    raise TestError("No keypair set for " + platformName)

  if profileProps.getBoolean(ec2.EC2_CREATE_GROUP_PROP):
    createdGroup = ec2.ensureGroup(group, profileProps)
    if createdGroup:
      # Authorize Hadoop ports too
      # aaron: I'm honestly not sure what ports Hadoop really needs here.
      # It might actually be more than this. I gave up and authorized all
      # traffic within the chdtest group manually; I don't know if this
      # will recreate everything necessary.
      ec2.authorizeGroup(group, 9000, TCP, profileProps)
      ec2.authorizeGroup(group, 9001, TCP, profileProps)
      ec2.authorizeGroup(group, 50010, TCP, profileProps)

  # The chdtest identity file may not be chmod'd to 0600 (git does not
  # track permissions except a+x/a-x). We need to do that here.
  identityFile = profileProps.getProperty("ssh.identity")
  if identityFile != None:
    shell.sh("chmod 0600 " + identityFile)

  if configOnly:
    # That's as far as we go!
    return []

  # throws shell.CommandError on failure.
  instances = ec2.runInstances(ami, instanceCount, group, keyPair, userData, \
      instanceType, zone, profileProps)

  # throws ec2.TimeoutError
  bootTimeout = DEFAULT_BOOT_TIMEOUT
  ec2.waitForInstances(instances, bootTimeout, profileProps)

  return instances




