# (c) Copyright 2008 Cloudera, Inc.
#
# module: distrotester.platform
#
# Represents platform-specific launch and installation capabilities.
# Here is where we control what EC2 image we launch for, and where we
# select the proper module to use to run the system setup procedures.

import logging
import os
import sys
import unittest

import com.cloudera.tools.ec2 as ec2
from   com.cloudera.util.properties import Properties

from   distrotester.setup.fedora8 import Fedora8Setup
from   distrotester.constants import *
from   distrotester.testerror import TestError


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

  # TODO (aaron): New platform? Add it to this list.
  if platformName == "fc8.i386":
    return Fedora8Setup("i386", properties)
  elif platformName == "fc8.x86_64":
    return Fedora8Setup("x86_64", properties)
  else:
    raise TestError("No Setup object available for platform: " + platformName)


def testSuiteForPlatform(platformName, properties):
  """ return a pyunit TestSuite for execution on the remote platform """

  # TODO (aaron): New platform? Add it to this list.
  if platformName == "fc8.i386":
    suite = unittest.makeSuite(StandaloneTest, 'test')
    return unittest.TestSuite([suite])
  elif platformName == "fc8.x86_64":
    suite = unittest.makeSuite(StandaloneTest, 'test')
    return unittest.TestSuite([suite])
  else:
    raise TestError("No test suite available for platform: " + platformName)


def launchInstances(platformName, properties):
  """ Launch one or more EC2 instances and return a list of instance ids. """

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
    ec2.ensureGroup(group, profileProps)

  # throws shell.CommandError on failure.
  instances = ec2.runInstances(ami, instanceCount, group, keyPair, userData, \
      instanceType, zone, profileProps)

  # throws ec2.TimeoutError
  bootTimeout = DEFAULT_BOOT_TIMEOUT
  ec2.waitForInstances(instances, bootTimeout, profileProps)

  return instances




