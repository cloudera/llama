# (c) Copyright 2009 Cloudera, Inc.
#
# module: distrotester.installtests.multihost
#
# Functionality unit tests for launching the installer on a 2-node
# cluster (one master node and one slave node)

import logging
import os
import socket
import tempfile
import unittest

import com.cloudera.distribution.sshall as sshall
from   com.cloudera.testutil.verbosetest import VerboseTestCase
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.installtests.installbase as installbase
import distrotester.testproperties as testproperties
from   distrotester.functiontests.hadooptests import HadoopTest
from   distrotester.functiontests.hivetests import HiveTest
from   distrotester.functiontests.logmovertests import LogMoverTest
from   distrotester.functiontests.pigtests import PigTest
from   distrotester.functiontests.scribetests import ScribeTest
import distrotester.functiontests.secondarynamenodetests as secondarynamenodetests

class MultiHostTest(installbase.InstallBaseTest):

  def __init__(self, methodName='runTest'):
    installbase.InstallBaseTest.__init__(self, methodName)

  def tearDown(self):
    installbase.InstallBaseTest.tearDown()

  def setUp(self):
    """ shutdown and remove existing hadoop distribution. """

    installbase.InstallBaseTest.setUp(self)

    try:
      self.stopHadoop()
    except:
      pass

    logging.debug("Performing setup actions for next multihost test")

    # Delete everything associated with Hadoop.
    # Do this on all hosts.

    self.doSshAll("killall scribed")

    # Delete things associated with scribe on all hosts
    self.doSshAll("rm -rf " + SCRIBE_OUT_DIR)

    #  Delete things associated with logmover on master
    shell.sh("rm -rf " + LOGMOVER_OUT_DIR)


  def start_hadoop(self, remote_secondary):
    shell.sh("service hadoop-namenode start")
    shell.sh("service hadoop-jobtracker start")

    # Do this on slaves only
    self.doSshAll("service hadoop-datanode start", True)
    self.doSshAll("service hadoop-tasktracker start", True)

    # Start 2nn on the appropriate host
    if remote_secondary != None:
      shell.ssh("root", remote_secondary, "service hadoop-secondarynamenode start", \
          self.getProperties())
    else:
      shell.sh("service hadoop-secondarynamenode start")


  def test_all_apps(self):
    """ Install all components.
        Use a separate hadoop user account and a separate client account. """

    # Install configuration and enable via alternatives
    self.enable_configuration("fast-checkpoint", socket.getfqdn())
    self.start_hadoop(None)

    self.getProperties().setProperty(HADOOP_USER_KEY, HADOOP_USER)
    self.getProperties().setProperty(CLIENT_USER_KEY, CLIENT_USER)

    # TODO(aaron): Enable tests as they're available
    hadoopSuite   = unittest.makeSuite(HadoopTest, 'test')
#    hiveSuite     = unittest.makeSuite(HiveTest, 'test')
#    logMoverSuite = unittest.makeSuite(LogMoverTest, 'test')
    pigSuite      = unittest.makeSuite(PigTest, 'test')
#    scribeSuite   = unittest.makeSuite(ScribeTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite,
#        hiveSuite,
#        logMoverSuite,
        pigSuite,
#        scribeSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()



  def test_separate_secondary(self):
    """ Test separate secondarynamenode.
        Install all components.
        Put the NN and JT on one machine.
        Put the 2NN on the other machine, along with DN and TT.
        Use a separate hadoop user account and a separate client account. """

    slavesList = self.getSlavesList()
    if len(slavesList) == 0:
      fail("No slaves available to check separate 2NN")
    secondary_node_addr = slavesList[0]

    # Install configuration and enable via alternatives
    self.enable_configuration("fast-checkpoint", socket.getfqdn())
    self.start_hadoop(secondary_node_addr)

    properties = self.getProperties()

    properties.setProperty(HADOOP_USER_KEY, HADOOP_USER)
    properties.setProperty(CLIENT_USER_KEY, CLIENT_USER)
    properties.setProperty(SECONDARY_HOSTNAME_KEY, secondary_node_addr)

    # TODO(aaron): Enable tests as they're available
    hadoopSuite   = unittest.makeSuite(HadoopTest, 'test')
    hiveSuite     = unittest.makeSuite(HiveTest, 'test')
#    logMoverSuite = unittest.makeSuite(LogMoverTest, 'test')
    pigSuite      = unittest.makeSuite(PigTest, 'test')
#    scribeSuite   = unittest.makeSuite(ScribeTest, 'test')
    secondarySuite = unittest.makeSuite(secondarynamenodetests.SecondaryNameNodeTest, 'test')

    functionalityTests = unittest.TestSuite([
        hadoopSuite,
        hiveSuite,
#        logMoverSuite,
        pigSuite,
#        scribeSuite,
        secondarySuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()

