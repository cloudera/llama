# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.piginstall
#
# Defines the ToolInstall instance that installs Pig

import os
import sys

from   com.cloudera.distribution.constants import *
import com.cloudera.distribution.dnsregex as dnsregex
import com.cloudera.distribution.env as env
from   com.cloudera.distribution.installerror import InstallError
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

      try:
        handle = open(confFilename, "a")
        handle.write("\n")
        handle.write("# Added by Cloudera Hadoop distribution installer\n")
        handle.write("cluster = %(cluster)s\n" % { "cluster" : jobTrackerAddr })
        handle.close()
      except IOError, ioe:
        raise InstallError("""Could not write to conf/pig.properties file.
Reason: %(ioe)s""" % { "ioe" : str(ioe) })


  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

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
