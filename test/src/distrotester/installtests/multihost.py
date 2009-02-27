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


  def start_hadoop(self):
    shell.sh("service hadoop-namenode start")
    shell.sh("service hadoop-jobtracker start")

    # Do this on slaves only
    self.doSshAll("service hadoop-datanode start", True)
    self.doSshAll("service hadoop-tasktracker start", True)

    # TODO(aaron): Start 2nn on the appropriate host


  def test_all_apps(self):
    """ Install all components.
        Use a separate hadoop user account and a separate client account. """

    platform_setup = self.getPlatformSetup()

    # install basic configuration
    platform_setup.installPackage("hadoop")

    # TODO(aaron): Install configuration and enable via alternatives

    # install virtual packages for services
    platform_setup.installPackage("hadoop-namenode")
    platform_setup.installPackage("hadoop-secondarynamenode")
    platform_setup.installPackage("hadoop-jobtracker")

    # install additional bundled applications
    platform_setup.installPackage("hadoop-pig")

    # TODO(aaron): Test MRUnit here.
    # platform_setup.installPackage("mrunit")

    # TODO(aaron): Install packages on slave nodes

    self.start_hadoop()

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


    # TODO(aaron): Rewrite this entire method.

    properties = self.getProperties()

    # Use the first slave as the 2nn.
    slavesList = self.getSlavesList()
    if len(slavesList) == 0:
      fail("No slaves available to check separate 2NN")
    secondary_node_addr = slavesList[0]

    hadoop_site_file = self.prepHadoopSite("hadoop-configs/fast-checkpoint.xml")

    cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
        + " --role namenode,jobtracker,scribe_master" \
        + " --config-prefix " + CONFIG_PREFIX \
        + " --log-filename " + INSTALLER_LOG_FILE \
        + " --format-hdfs --hadoop-user " + HADOOP_USER \
        + " --java-home " + javaHome \
        + " --hadoop-slaves " + self.getSlavesFile() \
        + " --identity /root/.ssh/id_rsa" \
        + " --hadoop-site " + hadoop_site_file \
        + ' --namenode "' + self.hostname + ':9000" ' \
        + ' --jobtracker "' + self.hostname + ':9001"' \
        + ' --secondary "' + secondary_node_addr + '"'\
        + " --overwrite-htdocs" \
        + " --debug"

    logging.debug("Installing with command: " + cmd)
    shell.sh(cmd)


    # Upload the prepped hadoop-site.xml file to the slave
    shell.scp(hadoop_site_file, "root", secondary_node_addr, hadoop_site_file, \
        properties)

    # Recompress the distribution, upload that to the slave, and uncompress it.
    new_tarball = "/mnt/recompressed-distro.tar.gz"
    unzip_dir = os.path.dirname(new_tarball)

    if DISTRIB_DEST_DIR.endswith(os.sep):
      recompress_target = DISTRIB_DEST_DIR[0:len(DISTRIB_DEST_DIR)-1]
    else:
      recompress_target = DISTRIB_DEST_DIR

    recompress_component = os.path.basename(recompress_target)
    recompress_basedir = os.path.dirname(recompress_target)

    if os.path.exists(new_tarball):
      os.unlink(new_tarball)
    cmd = "tar czf \"" + new_tarball + "\" -C \"" + recompress_basedir + "\" " \
        + "\"" + recompress_component + "\""
    shell.sh(cmd)

    shell.scp(new_tarball, "root", secondary_node_addr, new_tarball, properties)
    shell.ssh("root", secondary_node_addr, "tar zxf " + new_tarball + " -C " + unzip_dir, \
        properties)

    # Now actually run the installer on the other node
    logging.info("Performing second node installation")

    cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
        + " --role secondary_namenode,datanode,tasktracker,scribe_slave" \
        + " --config-prefix " + CONFIG_PREFIX \
        + " --log-filename " + INSTALLER_LOG_FILE \
        + " --format-hdfs --hadoop-user " + HADOOP_USER \
        + " --java-home " + javaHome \
        + " --identity /root/.ssh/id_rsa" \
        + " --hadoop-site " + hadoop_site_file \
        + ' --namenode ' + self.hostname + ':9000 ' \
        + ' --jobtracker ' + self.hostname + ':9001' \
        + ' --secondary ' + secondary_node_addr \
        + ' --scribe-master ' + self.hostname \
        + " --overwrite-htdocs" \
        + " --debug" \
        + " --standalone-secondarynamenode" # Necessary for this unique config.

    # pass -t to ssh to allow a tty for the remote installer.
    sshOpts = properties.getProperty("ssh.options", "")
    properties.setProperty("ssh.options", sshOpts + " -t")
    logging.debug("Performing remote install with command: " + cmd)
    shell.ssh("root", secondary_node_addr, cmd, properties)
    properties.setProperty("ssh.options", sshOpts)

    properties.setProperty(HADOOP_USER_KEY, HADOOP_USER)
    properties.setProperty(CLIENT_USER_KEY, CLIENT_USER)

    # Start Hadoop daemons.
    logging.debug("Starting Hadoop daemons...")
    start_daemons_cmd = os.path.join(self.getHadoopDir(), "bin/start-all.sh")
    cmd = "sudo -H -u " + HADOOP_USER + " " + start_daemons_cmd
    shell.sh(cmd)

    # set up server addr for use in this test.
    properties.setProperty(SECONDARY_HOSTNAME_KEY, secondary_node_addr)

    hadoopSuite      = unittest.makeSuite(HadoopTest, 'test')
    secondaryNNSuite = unittest.makeSuite(secondarynamenodetests.SecondaryNameNodeTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite,
        secondaryNNSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()

