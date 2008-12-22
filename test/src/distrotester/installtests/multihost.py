# (c) Copyright 2008 Cloudera, Inc.
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

from   com.cloudera.testutil.asserts import TestCaseWithAsserts
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties
from   distrotester.functiontests.hadooptests import HadoopTest
from   distrotester.functiontests.hivetests import HiveTest
from   distrotester.functiontests.pigtests import PigTest

class MultiHostTest(TestCaseWithAsserts):

  def __init__(self, methodName='runTest'):
    TestCaseWithAsserts.__init__(self, methodName)
    self.curHadoopSite = None
    self.curSlavesFile = None

    # Get our hostname and memoize it
    self.hostname = socket.getfqdn()


  def getPlatformSetup(self):
    """ Get the PlatformSetup object used to initialize the node """
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone

    # delaying this import til this thunk is used to avoid
    # circular dependency
    import distrotester.platforms as platforms

    properties = self.getProperties()
    platformName = properties.getProperty(TEST_PLATFORM_KEY)
    return platforms.setupForPlatform(platformName, properties)


  def getHadoopDir(self):
    return os.path.join(INSTALL_PREFIX, "hadoop")

  def getHadoopCmd(self):
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone
    return os.path.join(self.getHadoopDir(), "bin/hadoop")

  def getSlavesFile(self):
    """ Return the slaves file provided by the remote test manager """
    return self.getProperties().getProperty(SLAVES_FILE_KEY)


  def getProperties(self):
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone
    return testproperties.getProperties()

  def prepHadoopSite(self, inputHadoopSite):
    """ given an input hadoop-site.xml file we want to use, we must
        first replace the 'MASTER_HOST' string with the current
        hostname.
    """

    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone

    # Get a temporary filename to use as the hadoop-site.xml file.
    (oshandle, tmpFilename) = tempfile.mkstemp()
    self.curHadoopSite = tmpFilename
    try:
      handle = os.fdopen(oshandle, "w")
      handle.close()
    except OSError:
      # irrelevant
      pass
    except IOError:
      # irrelevant
      pass

    # put the hadoop site file through a sed script.
    script = 'sed -e "s/MASTER_HOST/' + self.hostname + '/" ' \
        + inputHadoopSite + " > " + self.curHadoopSite
    shell.sh(script)

    return self.curHadoopSite

  def stopHadoopForUser(self, user):
    """ Stop the hadoop daemons run by a given hadoop username """
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone
    try:
      # stop any hadoop run as hadoop user.
      hadoopDir = self.getHadoopDir()
      stopCmd = os.path.join(hadoopDir, "bin/stop-all.sh")
      shell.sh("sudo -H -u " + user + " " + stopCmd)
    except shell.CommandError, ce:
      pass # nothing to shut down? ok

  def stopHadoop(self):
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone
    self.stopHadoopForUser(ROOT_USER)
    self.stopHadoopForUser(HADOOP_USER)



  def tearDown(self):
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone
    if self.curHadoopSite != None:
      # remove this temp file we created
      os.remove(self.curHadoopSite)

    if self.curSlavesFile != None:
      # remove this temp file we created
      os.remove(self.curSlavesFile)

    # self.stopHadoop()


  def setUp(self):
    """ shutdown and remove existing hadoop distribution. """
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone

    try:
      self.stopHadoop()
    except:
      pass

    # Delete everything associated with Hadoop.
    shell.sh("rm -rf " + BASE_TMP_DIR)
    shell.sh("rm -rf " + INSTALL_PREFIX)
    shell.sh("rm -rf " + CONFIG_PREFIX)
    shell.sh("mkdir -p " + BASE_TMP_DIR)
    shell.sh("chmod a+w " + BASE_TMP_DIR)
    shell.sh("chmod o+t " + BASE_TMP_DIR)



  def testHadoopOnly(self):
    """ Install *only* the hadoop component.
        Install, run, and use hadoop as root
    """

    javaHome = self.getProperties().getProperty(JAVA_HOME_KEY)

    cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
        + " --without-scribe --without-pig --without-hive" \
        + " --config-prefix " + CONFIG_PREFIX \
        + " --log-filename " + INSTALLER_LOG_FILE \
        + " --format-hdfs --hadoop-user root " \
        + " --java-home " + javaHome \
        + " --hadoop-slaves " + self.getSlavesFile() \
        + " --identity /root/.ssh/id_rsa" \
        + " --hadoop-site " \
        + self.prepHadoopSite("hadoop-configs/basic-config.xml") \
        + " --debug"
    logging.debug("Installing with command: " + cmd)
    shell.sh(cmd)

    self.getProperties().setProperty(HADOOP_USER_KEY, ROOT_USER)
    self.getProperties().setProperty(CLIENT_USER_KEY, ROOT_USER)

    hadoopSuite = unittest.makeSuite(HadoopTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()


  def testAllApps(self):
    """ Install all components.
        Use a separate hadoop user account and a separate client account. """

    # TODO (aaron): enable scribe when it's ready.
    javaHome = self.getProperties().getProperty(JAVA_HOME_KEY)

    cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
        + " --without-scribe " \
        + " --config-prefix " + CONFIG_PREFIX \
        + " --log-filename " + INSTALLER_LOG_FILE \
        + " --format-hdfs --hadoop-user " + HADOOP_USER \
        + " --java-home " + javaHome \
        + " --hadoop-slaves " + self.getSlavesFile() \
        + " --identity /root/.ssh/id_rsa" \
        + " --hadoop-site " \
        + self.prepHadoopSite("hadoop-configs/basic-config.xml") \
        + ' --namenode "hdfs://' + self.hostname + ':9000/" ' \
        + ' --jobtracker "' + self.hostname + ':9001"' \
        + " --debug"

    logging.debug("Installing with command: " + cmd)
    shell.sh(cmd)

    self.getProperties().setProperty(HADOOP_USER_KEY, HADOOP_USER)
    self.getProperties().setProperty(CLIENT_USER_KEY, CLIENT_USER)

    hadoopSuite = unittest.makeSuite(HadoopTest, 'test')
    hiveSuite   = unittest.makeSuite(HiveTest, 'test')
    pigSuite    = unittest.makeSuite(PigTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite,
        hiveSuite,
        pigSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()

  def testWithHostsFiles(self):
    """ All apps, separate accounts, with dfs.hosts and dfs.hosts.exclude
        files set in place.
    """

    # TODO: Enable scribe when it's ready.
    javaHome = self.getProperties().getProperty(JAVA_HOME_KEY)

    cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
        + " --without-scribe " \
        + " --config-prefix " + CONFIG_PREFIX \
        + " --log-filename " + INSTALLER_LOG_FILE \
        + " --format-hdfs --hadoop-user " + HADOOP_USER \
        + " --java-home " + javaHome \
        + " --hadoop-slaves " + self.getSlavesFile() \
        + " --identity /root/.ssh/id_rsa" \
        + " --make-dfs-hosts dfs.hosts" \
        + " --make-dfs-excludes dfs.hosts.exclude" \
        + " --hadoop-site " \
        + self.prepHadoopSite("hadoop-configs/hosts-config.xml") \
        + ' --namenode "hdfs://' + self.hostname + ':9000/" ' \
        + ' --jobtracker "' + self.hostname + ':9001"' \
        + " --debug"

    shell.sh(cmd)

    self.getProperties().setProperty(HADOOP_USER_KEY, HADOOP_USER)
    self.getProperties().setProperty(CLIENT_USER_KEY, CLIENT_USER)

    hadoopSuite = unittest.makeSuite(HadoopTest, 'test')
    hiveSuite   = unittest.makeSuite(HiveTest, 'test')
    pigSuite    = unittest.makeSuite(PigTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite,
        hiveSuite,
        pigSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()
    pass

  def testGuidedInstall(self):
    """ Use stdin to handle guided installation. """
    # TODO: This
    # TODO: set the editor to /bin/true, provide a slaves file.
    pass


