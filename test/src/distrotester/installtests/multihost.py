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
import distrotester.testproperties as testproperties
from   distrotester.functiontests.hadooptests import HadoopTest
from   distrotester.functiontests.hivetests import HiveTest
from   distrotester.functiontests.logmovertests import LogMoverTest
from   distrotester.functiontests.pigtests import PigTest
from   distrotester.functiontests.scribetests import ScribeTest
import distrotester.functiontests.secondarynamenodetests as secondarynamenodetests

class MultiHostTest(VerboseTestCase):

  def __init__(self, methodName='runTest'):
    VerboseTestCase.__init__(self, methodName)
    self.curHadoopSite = None

    # Get our hostname and memoize it
    self.hostname = socket.getfqdn()


  def getPlatformSetup(self):
    """ Get the PlatformSetup object used to initialize the node """
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone (CH-77)

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
    # multihost and standalone (CH-77)
    return os.path.join(self.getHadoopDir(), "bin/hadoop")

  def getSlavesFile(self):
    """ Return the slaves file provided by the remote test manager """
    return self.getProperties().getProperty(SLAVES_FILE_KEY)

  def getSlavesList(self):
    slavesFile = self.getSlavesFile()
    try:
      handle = open(slavesFile)
      lines = handle.readlines()
      slavesList = []
      for line in lines:
        slavesList.append(line.strip())
      handle.close()
      return slavesList
    except IOError, ioe:
      logging.error("Error opening slaves file: " + str(slavesFile))
      logging.error(ioe)
      return []


  def getProperties(self):
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone (CH-77)
    return testproperties.getProperties()

  def prepHadoopSite(self, inputHadoopSite):
    """ given an input hadoop-site.xml file we want to use, we must
        first replace the 'MASTER_HOST' string with the current
        hostname.
    """

    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone (CH-77)

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
    # multihost and standalone (CH-77)
    try:
      # stop any hadoop run as hadoop user.
      hadoopDir = self.getHadoopDir()
      stopCmd = os.path.join(hadoopDir, "bin/stop-all.sh")
      shell.sh("sudo -H -u " + user + " " + stopCmd)
    except shell.CommandError, ce:
      pass # nothing to shut down? ok

  def stopHadoop(self):
    # TODO(aaron): Refactor this out into common base class for
    # multihost and standalone (CH-77)
    self.stopHadoopForUser(ROOT_USER)
    self.stopHadoopForUser(HADOOP_USER)


  def tearDown(self):
    # TODO(aaron): This code block is common to MultiHost and Standalone
    # (CH-77)
    if self.curHadoopSite != None:
      # remove this temp file we created
      os.remove(self.curHadoopSite)

    # self.stopHadoop()
    VerboseTestCase.tearDown(self)


  def doSshAll(self, cmd):
    " Execute a command on all hosts. "

    logging.debug("sshall command: " + cmd)

    allHosts = self.getSlavesList()
    allHosts.append(self.hostname)

    results = sshall.sshMultiHosts("root", allHosts, cmd, \
        self.getProperties(), SSH_RETRIES, SSH_PARALLEL)
    for result in results:
      # each result is an sshall.SshResult obj
      if result.getStatus() != 0:
        logging.error("Got error status executing command on " \
            + result.getHost())
        logging.error("Output:")
        for line in result.getOutput():
          logging.error("  " + line.rstrip())

  def setUp(self):
    """ shutdown and remove existing hadoop distribution. """

    VerboseTestCase.setUp(self)

    try:
      self.stopHadoop()
    except:
      pass

    logging.debug("Performing setup actions for next multihost test")

    # Delete everything associated with Hadoop.
    # Do this on all hosts.

    self.doSshAll("killall scribed")
    self.doSshAll("rm -rf " + BASE_TMP_DIR)
    self.doSshAll("rm -rf " + INSTALL_PREFIX)
    self.doSshAll("rm -rf " + CONFIG_PREFIX)
    self.doSshAll("mkdir -p " + BASE_TMP_DIR)
    self.doSshAll("chmod a+w " + BASE_TMP_DIR)
    self.doSshAll("chmod o+t " + BASE_TMP_DIR)

    # Delete things associated with scribe on all hosts
    self.doSshAll("rm -rf " + SCRIBE_OUT_DIR)

    #  Delete things associated with logmover on master
    shell.sh("rm -rf " + LOGMOVER_OUT_DIR)


  def testHadoopOnly(self):
    """ Install *only* the hadoop component.
        Install, run, and use hadoop as root
    """

    javaHome = self.getProperties().getProperty(JAVA_HOME_KEY)

    cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
        + " --role jobtracker,namenode,secondary_namenode,deployment_master" \
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

  def testCreateSshKeys(self):
    """ Removes ~hadoop/.ssh/id_rsa; attempts to ensure that this can still
        be regenerated by the installer.
        Then install *only* the hadoop component.
        Run in multi-user mode.
    """

    javaHome = self.getProperties().getProperty(JAVA_HOME_KEY)

    logging.debug("Initial ssh key md5:")
    id_rsa_md5 = shell.shLines("md5sum /home/hadoop/.ssh/id_rsa")
    logging.debug(id_rsa_md5)
    logging.debug("Removing id_rsa file for hadoop user")
    os.rename("/home/hadoop/.ssh/id_rsa", "/home/hadoop/.ssh/id_rsa_backup")

    try:
      cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
          + " --role jobtracker,namenode,secondary_namenode,deployment_master" \
          + " --config-prefix " + CONFIG_PREFIX \
          + " --log-filename " + INSTALLER_LOG_FILE \
          + " --format-hdfs --hadoop-user " + HADOOP_USER \
          + " --java-home " + javaHome \
          + " --hadoop-slaves " + self.getSlavesFile() \
          + " --identity /root/.ssh/id_rsa" \
          + " --create-keys" \
          + " --hadoop-site " \
          + self.prepHadoopSite("hadoop-configs/basic-config.xml") \
          + " --debug"
      logging.debug("Installing with command: " + cmd)
      shell.sh(cmd)

      self.getProperties().setProperty(HADOOP_USER_KEY, HADOOP_USER)
      self.getProperties().setProperty(CLIENT_USER_KEY, CLIENT_USER)

      hadoopSuite = unittest.makeSuite(HadoopTest, 'test')
      functionalityTests = unittest.TestSuite([
          hadoopSuite
          ])

      print "Running Hadoop functionality tests"
      runner = unittest.TextTestRunner()
      if not runner.run(functionalityTests).wasSuccessful():
        self.fail()
    finally:
      # Restore the original id_rsa file on our way out.
      # Also, delete the id_rsa.pub.cloudera file that we leave behind.
      os.rename("/home/hadoop/.ssh/id_rsa_backup", "/home/hadoop/.ssh/id_rsa")
      os.remove("/home/hadoop/.ssh/id_rsa.pub.cloudera")
      logging.debug("Final ssh key md5:")
      id_rsa_md5 = shell.shLines("md5sum /home/hadoop/.ssh/id_rsa")
      logging.debug(id_rsa_md5)


  def testAllApps(self):
    """ Install all components.
        Use a separate hadoop user account and a separate client account. """

    javaHome = self.getProperties().getProperty(JAVA_HOME_KEY)

    cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
        + " --role master,developer" \
        + " --config-prefix " + CONFIG_PREFIX \
        + " --log-filename " + INSTALLER_LOG_FILE \
        + " --format-hdfs --hadoop-user " + HADOOP_USER \
        + " --java-home " + javaHome \
        + " --hadoop-slaves " + self.getSlavesFile() \
        + " --identity /root/.ssh/id_rsa" \
        + " --hadoop-site " \
        + self.prepHadoopSite("hadoop-configs/basic-config.xml") \
        + ' --namenode "' + self.hostname + ':9000" ' \
        + ' --jobtracker "' + self.hostname + ':9001"' \
        + " --overwrite-htdocs" \
        + " --debug"

    logging.debug("Installing with command: " + cmd)
    shell.sh(cmd)

    self.getProperties().setProperty(HADOOP_USER_KEY, HADOOP_USER)
    self.getProperties().setProperty(CLIENT_USER_KEY, CLIENT_USER)

    hadoopSuite   = unittest.makeSuite(HadoopTest, 'test')
    hiveSuite     = unittest.makeSuite(HiveTest, 'test')
    logMoverSuite = unittest.makeSuite(LogMoverTest, 'test')
    pigSuite      = unittest.makeSuite(PigTest, 'test')
    scribeSuite   = unittest.makeSuite(ScribeTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite,
        hiveSuite,
        logMoverSuite,
        pigSuite,
        scribeSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()


  def testWithHostsFiles(self):
    """ All apps, separate accounts, with dfs.hosts and dfs.hosts.exclude
        files set in place.
    """

    javaHome = self.getProperties().getProperty(JAVA_HOME_KEY)

    cmd = INSTALLER_COMMAND + " --unattend --prefix " + INSTALL_PREFIX \
        + " --role master,developer" \
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
        + ' --namenode "' + self.hostname + ':9000" ' \
        + ' --jobtracker "' + self.hostname + ':9001"' \
        + " --overwrite-htdocs" \
        + " --debug"

    shell.sh(cmd)

    self.getProperties().setProperty(HADOOP_USER_KEY, HADOOP_USER)
    self.getProperties().setProperty(CLIENT_USER_KEY, CLIENT_USER)

    hadoopSuite = unittest.makeSuite(HadoopTest, 'test')
    hiveSuite   = unittest.makeSuite(HiveTest, 'test')
    pigSuite    = unittest.makeSuite(PigTest, 'test')
    scribeSuite = unittest.makeSuite(ScribeTest, 'test')
    functionalityTests = unittest.TestSuite([
        hadoopSuite,
        hiveSuite,
        pigSuite,
        scribeSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()
    pass

  def testGuidedInstall(self):
    """ Use stdin to handle guided installation. """
    # TODO: This (CH-79)
    # TODO: set the editor to /bin/true, provide a slaves file.
    pass


  def testDoubleInstall(self):
    """ Run the installer 2x in a row then test """

    # This tests CH-118 - "installer should stop scribe, hadoop daemons"

    logging.info("Performing first install/test in repeated batch.")
    self.testAllApps()
    # the second installation will reformat the hdfs instance, but
    # we need to manually destroy the hdfs data dir first or else
    # it will fail to boot (bad namespace).
    self.doSshAll("rm -rf /mnt/tmp/data")
    logging.info("Performing second install/test in repeated batch.")
    self.testAllApps()


  def testSeparateSecondary(self):
    """ Test separate secondarynamenode.
        Install all components.
        Put the NN and JT on one machine.
        Put the 2NN on the other machine, along with DN and TT.
        Use a separate hadoop user account and a separate client account. """

    javaHome = self.getProperties().getProperty(JAVA_HOME_KEY)

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
        self.getProperties())

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

    shell.scp(new_tarball, "root", secondary_node_addr, new_tarball, self.getProperties())
    shell.ssh("root", secondary_node_addr, "tar zxf " + new_tarball + " -C " + unzip_dir, \
        self.getProperties())

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
        + " --debug"

    # TODO(aaron) pass -t to ssh to allow a tty here.
    logging.debug("Performing remote install with command: " + cmd)
    shell.ssh("root", secondary_node_addr, cmd, self.getProperties())

    self.getProperties().setProperty(HADOOP_USER_KEY, HADOOP_USER)
    self.getProperties().setProperty(CLIENT_USER_KEY, CLIENT_USER)

    # Start Hadoop daemons.
    logging.debug("Starting Hadoop daemons...")
    start_daemons_cmd = os.path.join(self.getHadoopDir(), "bin/start-all.sh")
    cmd = "sudo -H -u " + HADOOP_USER + " " + start_daemons_cmd
    shell.sh(cmd)

    # set up server addr for use in this test.
    self.getProperties().setProperty(SECONDARY_HOSTNAME_KEY, secondary_node_addr)

    hadoopSuite      = unittest.makeSuite(HadoopTest, 'test')
    secondaryNNSuite = unittest.makeSuite(secondarynamenodetests.SecondaryNameNodeTest, 'test')
    functionalityTests = unittest.TestSuite([
      # TODO: Enable this.
      #  hadoopSuite,
        secondaryNNSuite
        ])

    print "Running Hadoop functionality tests"
    runner = unittest.TextTestRunner()
    if not runner.run(functionalityTests).wasSuccessful():
      self.fail()
