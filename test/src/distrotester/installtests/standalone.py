# (c) Copyright 2009 Cloudera, Inc.
#
# module: distrotester.installtests.standalone
#
# Functionality unit tests for launching the installer on a single-node
# (standalone) "cluster"

import logging
import os
import socket
import tempfile
import unittest

from   com.cloudera.testutil.verbosetest import VerboseTestCase
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties
import distrotester.installtests.installbase as installbase
from   distrotester.functiontests.hadooptests import HadoopTest
from   distrotester.functiontests.hivetests import HiveTest
from   distrotester.functiontests.logmovertests import LogMoverTest
from   distrotester.functiontests.pigtests import PigTest
from   distrotester.functiontests.scribetests import ScribeTest

class StandaloneTest(installbase.InstallBaseTest):

  def __init__(self, methodName='runTest'):
    installbase.InstallBaseTest.__init__(self, methodName)
    self.curSlavesFile = None

  def getSlavesFile(self):
    """ Write out a slaves file containing only 'localhost' """

    (oshandle, tmpFilename) = tempfile.mkstemp("", "slaves-")
    self.curSlavesFile = tmpFilename

    handle = os.fdopen(oshandle, "w")
    handle.write(self.hostname + "\n")
    handle.close()

    return tmpFilename


  def stopScribe(self):
    try:
      shell.sh("killall scribed")
    except shell.CommandError, ce:
      pass # nothing to shut down? ok.


  def tearDown(self):
    if self.curSlavesFile != None:
      # remove this temp file we created
      os.remove(self.curSlavesFile)

    installbase.InstallBaseTest.tearDown(self)


  def setUp(self):
    """ shutdown and remove existing hadoop distribution. """

    installbase.InstallBaseTest.setUp(self)

    try:
      self.stop_hadoop()
      self.stopScribe()
    except:
      pass


  def start_hadoop(self):
    shell.sh("service hadoop-namenode start")
    shell.sh("service hadoop-secondarynamenode start")
    shell.sh("service hadoop-datanode start")
    shell.sh("service hadoop-jobtracker start")
    shell.sh("service hadoop-tasktracker start")


  def test_all_apps(self):
    """ Install all components.
        Use a separate hadoop user account and a separate client account. """

    # install basic configuration
    platform_setup = self.get_platform_setup()
    platform_setup.installPackage("hadoop-conf-pseudo")

    self.start_hadoop()

    self.getProperties().setProperty(HADOOP_USER_KEY, HADOOP_USER)
    self.getProperties().setProperty(CLIENT_USER_KEY, CLIENT_USER)

    hadoopSuite   = unittest.makeSuite(HadoopTest, 'test')
# TODO(aaron): Enable tests as they're available.
    hiveSuite     = unittest.makeSuite(HiveTest, 'test')
    pigSuite      = unittest.makeSuite(PigTest, 'test')
#    scribeSuite   = unittest.makeSuite(ScribeTest, 'test')
#    logmoverSuite = unittest.makeSuite(LogMoverTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite,
        hiveSuite,
        pigSuite,
#        scribeSuite,
#        logmoverSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()

