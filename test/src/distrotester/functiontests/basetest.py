# (c) Copyright 2009 Cloudera, Inc.
#
# Functionality unit test cases for Hadoop

import logging
import os

from   com.cloudera.testutil.verbosetest import VerboseTestCase
import com.cloudera.tools.shell as shell

from   distrotester.constants import *
import distrotester.testproperties as testproperties


class BaseTest(VerboseTestCase):

  def getInstallRoot(self):
    return INSTALL_PREFIX

  def getHadoopDir(self):
    return "/usr/lib/hadoop"

  def getProperties(self):
    return testproperties.getProperties()

  def getHadoopCmd(self):
    return "/usr/bin/hadoop"

  def getClientSudo(self):
    """ Return the shell cmd prefix to access a client hadoop program """
    clientUser = self.getProperties().getProperty(CLIENT_USER_KEY)
    if clientUser != ROOT_USER:
      return "sudo -H -u " + clientUser + " "
    else:
      return ""

  def getDaemonSudo(self):
    """ Return the shell cmd prefix to run a superuser hadoop program """
    superUser = self.getProperties().getProperty(HADOOP_USER_KEY)
    if superUser != ROOT_USER:
      return "sudo -H -u " + superUser + " "
    else:
      return ""


  def getTestJar(self):
    """ Return the filename of the jar containing the Java code to run """
    return DISTRIB_TEST_JAR


  def tearDown(self):
    VerboseTestCase.tearDown(self)


  def setUp(self):
    """ Perform setup tasks for tests """

    VerboseTestCase.setUp(self)

    if not self.getProperties().getBoolean(IS_HOME_DIR_SETUP_KEY):
      # Ensure that the user's home dir exists in HDFS
      clientUser = self.getProperties().getProperty(CLIENT_USER_KEY)
      cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -mkdir /user/" \
          + clientUser
      try:
        shell.sh(cmd)
      except shell.CommandError, ce:
        pass # ok for this to cause error (if dir already exists)

      cmd = self.getDaemonSudo() + self.getHadoopCmd() + " fs -chown " \
          + clientUser + " /user/" + clientUser
      shell.sh(cmd)

      self.getProperties().setProperty(IS_HOME_DIR_SETUP_KEY, True)


