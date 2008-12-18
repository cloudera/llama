# (c) Copyright 2008 Cloudera, Inc.
#
# module: distrotester.installtests.standalone
#
# Functionality unit tests for launching the installer on a single-node
# (standalone) "cluster"

import logging
import os
import tempfile
import unittest

from   com.cloudera.testutil.asserts import TestCaseWithAsserts
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties
from   distrotester.functiontests.hadooptests import HadoopTest

class StandaloneTest(TestCaseWithAsserts):

  def __init__(self, methodName='runTest'):
    TestCaseWithAsserts.__init__(self, methodName)
    self.hostname = None
    self.curHadoopSite = None
    self.curSlavesFile = None

  def getHadoopDir(self):
    return os.path.join(INSTALL_PREFIX, "hadoop")

  def getHadoopCmd(self):
    return os.path.join(self.getHadoopDir(), "bin/hadoop")

  def getSlavesFile(self):
    """ Write out a slaves file containing only 'localhost' """

    (oshandle, tmpFilename) = tempfile.mkstemp("", "slaves-")
    self.curSlavesFile = tmpFilename

    handle = os.fdopen(oshandle, "w")
    handle.write("localhost\n")
    handle.close()

    return tmpFilename

  def getProperties(self):
    return testproperties.getProperties()

  def prepHadoopSite(self, inputHadoopSite):
    """ given an input hadoop-site.xml file we want to use, we must
        first replace the 'MASTER_HOST' string with the current
        hostname.
    """

    # Get our hostname and memoize it if need be.
    if self.hostname == None:
      hostLines = shell.shLines("hostname")
      if len(hostLines) > 0:
        self.hostname = hostLines[0].strip()

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

  def stopHadoop(self):
    try:
      # stop any hadoop run as ourselves.
      hadoopDir = self.getHadoopDir()
      stopCmd = os.path.join(hadoopDir, "bin/stop-all.sh")
      shell.sh(stopCmd)
    except shell.CommandError, ce:
      pass # nothing to shut down? ok

    try:
      # stop any hadoop run as hadoop user.
      hadoopDir = self.getHadoopDir()
      stopCmd = os.path.join(hadoopDir, "bin/stop-all.sh")
      shell.sh("sudo -H -u " + HADOOP_USER + " " + stopCmd)
    except shell.CommandError, ce:
      pass # nothing to shut down? ok


  def tearDown(self):
    if self.curHadoopSite != None:
      # remove this temp file we created
      os.remove(self.curHadoopSite)

    if self.curSlavesFile != None:
      # remove this temp file we created
      os.remove(self.curSlavesFile)

    # self.stopHadoop()


  def prepare(self):
    """ shutdown and remove existing hadoop distribution. """

    try:
      self.stopHadoop()

      # Delete everything associated with Hadoop.
      shell.sh("rm -rf /mnt/tmp")
      shell.sh("rm -rf " + INSTALL_PREFIX)
      shell.sh("rm -rf " + CONFIG_PREFIX)
    except:
      pass



  def testHadoopOnly(self):
    """ Install *only* the hadoop component.
        Install, run, and use hadoop as root
    """

    self.prepare()
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
    shell.sh(cmd)

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

    # TODO: Create accounts
    # TODO (aaron): enable scribe when it's ready.
    pass

  def testWithHostsFiles(self):
    """ All apps, separate accounts, with dfs.hosts and dfs.hosts.exclude
        files set in place.
    """
    # TODO this
    pass

  def testGuidedInstall(self):
    """ Use stdin to handle guided installation. """
    # TODO: This
    # TODO: set the editor to /bin/true, provide a slaves file.
    pass

