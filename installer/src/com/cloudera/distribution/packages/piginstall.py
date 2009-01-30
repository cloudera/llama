# (c) Copyright 2009 Cloudera, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# module: com.cloudera.distribution.packages.piginstall
#
# Defines the ToolInstall instance that installs Pig

import pickle
import logging
import os
import sys

from   com.cloudera.distribution.constants import *
import com.cloudera.distribution.dnsregex as dnsregex
import com.cloudera.distribution.env as env
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.postinstall as postinstall
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.tools.dirutils as dirutils
import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output
import com.cloudera.util.prompt as prompt


class PigInstall(toolinstall.ToolInstall):
  def __init__(self, properties):
    toolinstall.ToolInstall.__init__(self, "Pig", properties)
    self.addDependency("Hadoop")
    self.addDependency("GlobalPrereq")
    self.jobTrackerAddr = None


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    pass


  def getJobTrackerAddr(self):
    return self.jobTrackerAddr

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """

    # First, try to read from cmdline option for hadoop jobtracker
    self.jobTrackerAddr = self.properties.getProperty(PIG_JOBTRACKER_KEY)

    # If it's not there, and if Hadoop is being configured 'online',
    # we can get the jobtracker address from the hadoop installer.
    # Otherwise, we need to ask for this address ourselves.
    hadoopTool = toolinstall.getToolByName("Hadoop")
    if hadoopTool != None and self.jobTrackerAddr == None:
      try:
        self.jobTrackerAddr = \
            hadoopTool.getHadoopSiteProperty(MAPRED_JOB_TRACKER)
      except KeyError:
        # couldn't find it.
        pass

    if self.jobTrackerAddr == None and not self.isUnattended():
      matchesRegex = False
      while not matchesRegex:
        self.jobTrackerAddr = prompt.getString( \
            "Input the JobTracker address for Pig to connect to", \
            None, True)
        if not dnsregex.isDnsNameAndPort(self.jobTrackerAddr):
          output.printlnError("Error: must be of the form dnsname:port")
        else:
          matchesRegex = True

    if self.jobTrackerAddr == None:
      raise InstallError("Installing Pig requires that you specify a " \
          + "JobTracker address with --jobtracker")

    # Verify this address against dns:port
    if not dnsregex.isDnsNameAndPort(self.jobTrackerAddr):
      raise InstallError("JobTracker address " + self.jobTrackerAddr \
          + " must be of the form dnsname:port")

    output.printlnVerbose("Pig got jobtracker address: "+ self.jobTrackerAddr)


  def getFinalInstallPath(self):
    return os.path.join(self.getInstallBasePath(), PIG_INSTALL_SUBDIR)

  def install(self):
    """ Run the installation itself. """

    self.unzipPackage()
    self.setupHadoopLink()
    self.writeConfig()

  def unzipPackage(self):
    # install Pig by unzipping the package
    pigPackageName = os.path.abspath(os.path.join(PACKAGE_PATH, \
        PIG_PACKAGE))
    if not os.path.exists(pigPackageName):
      raise InstallError("Missing pig installation package " \
          + pigPackageName)

    installPath = self.getInstallBasePath()
    dirutils.mkdirRecursive(installPath)

    if self.properties.getBoolean("output.verbose", False):
      verboseChar = "v"
    else:
      verboseChar = ""
    cmd = "tar -" + verboseChar + "zxf \"" + pigPackageName + "\"" \
        + " -C \"" + installPath + "\""

    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error unpacking pig (tar returned error)")

  def setupHadoopLink(self):
    # pig expects lib/hadoop18.jar to exist. We don't want to ship a separate
    # jar; this should be a symlink to our distributed hadoop jar.
    hadoopLinkName = os.path.join(self.getFinalInstallPath(),
        "lib/hadoop18.jar")
    hadoopInstaller = toolinstall.getToolByName("Hadoop")
    if None == hadoopInstaller:
      raise InstallError("Pig cannot be installed without Hadoop")
    hadoopJarName = os.path.join(hadoopInstaller.getFinalInstallPath(),
        "hadoop-" + HADOOP_VERSION + "-core.jar")

    if not os.path.exists(hadoopJarName):
      raise InstallError("Cannot find Hadoop jar: " + hadoopJarName)

    if os.path.exists(hadoopLinkName):
      os.remove(hadoopLinkName)

    cmd = "ln -s \"" + hadoopJarName + "\" \"" + hadoopLinkName + "\""
    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error linking from Pig to Hadoop jar")

  def writeConfig(self):
    """ Write out any configuration files."""

    # conf/pig.properties needs to have a line:
    # cluster=hadoop-jobtracker-name:port
    # we must add this line here, if the user's configured a jobtracker
    jobTrackerAddr = self.getJobTrackerAddr()
    if jobTrackerAddr != None:
      confFilename = os.path.join(self.getFinalInstallPath(),
          "conf/pig.properties")
      hadoopInstaller = toolinstall.getToolByName("Hadoop")
      if None == hadoopInstaller:
        raise InstallError("Pig cannot be installed without Hadoop")

      output.printlnVerbose("Writing pig properties file: " + confFilename)
      try:
        handle = open(confFilename, "a")
        handle.write("\n")
        handle.write("# Added by Cloudera Hadoop distribution installer\n")
        handle.write("cluster = %(cluster)s\n" % { "cluster" : jobTrackerAddr })
        handle.close()
      except IOError, ioe:
        raise InstallError("""Could not write to conf/pig.properties file.
Reason: %(ioe)s""" % { "ioe" : str(ioe) })
    else:
      output.printlnInfo("Warning: not writing pig.properties.")


  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    if self.has_role("pig_developer"):
      self.createInstallSymlink("pig")
      configDirSrc = os.path.join(self.getFinalInstallPath(), "conf")
      self.createEtcSymlink("pig", configDirSrc)

      # Pig requires that users set several environment variables; calculate
      # their values and add them to the list for the user here.

      hadoopInstaller = toolinstall.getToolByName("Hadoop")
      if None == hadoopInstaller:
        raise InstallError("Pig cannot be installed without Hadoop")

      globalPrereq = toolinstall.getToolByName("GlobalPrereq")
      if None == globalPrereq:
        raise InstallError("Missing global prereq installer")

      javaDir = globalPrereq.getJavaHome()
      hadoopDir = os.path.join(hadoopInstaller.getFinalInstallPath(), "conf")
      pigDir = self.getFinalInstallPath()

      env.addToEnvironment("JAVA_HOME", javaDir)
      env.addToEnvironment("HADOOPDIR", hadoopDir)
      env.addToEnvironment("PIGDIR", pigDir)
      env.addToEnvironment("PIG_CLASSPATH", \
          os.path.join(pigDir, "pig-" + PIG_VERSION + "-core.jar") + ":" \
          + hadoopDir)

    if self.has_role("pig_master"):
      # must create pig tmp directory in HDFS before use
      hadoopInstaller = toolinstall.getToolByName("Hadoop")
      if hadoopInstaller == None:
        raise InstallError("Pig depends on Hadoop")

      postinstall.add(hadoopInstaller.get_start_hdfs_cmd())

      hadoop_cmdline = hadoopInstaller.get_hadoop_cmdline()
      postinstall.add(hadoop_cmdline + "dfsadmin -safemode wait")
      postinstall.add(hadoop_cmdline + "fs -mkdir " + PIG_TEMP_DIR, False)
      postinstall.add(hadoop_cmdline + "fs -chmod a+w " + PIG_TEMP_DIR)


  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify Pig
    pass


  def getRedeployArgs(self):
    argList = []

    jobTrackerAddr = self.getJobTrackerAddr()
    if jobTrackerAddr != None:
      argList.append("--jobtracker")
      argList.append(jobTrackerAddr)

    return argList


  def printFinalInstructions(self):
    pass

  def preserve_state(self, handle):
    pmap = {
      "job_tracker_addr" : self.jobTrackerAddr
    }

    pickle.dump(pmap, handle)

  def restore_state(self, handle, role_list, version):
    self.role_list = role_list
    pmap = pickle.load(handle)

    if version == "0.2.0":
      self.restore_0_2_0(pmap)
    else:
      raise InstallError("Cannot read state from file for version " + version)

  def restore_0_2_0(self, pmap):
    """ Read an 0.2.0 formatted pickle map """

    self.jobTrackerAddr = pmap["job_tracker_addr"]




