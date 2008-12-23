# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.scribeinstall
#
# Defines the ToolInstall instance that installs Scribe

import os
import socket
import time

import com.cloudera.distribution.arch as arch
from   com.cloudera.distribution.constants import *
import com.cloudera.distribution.dnsregex as dnsregex
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.util.output as output
import com.cloudera.util.prompt as prompt
import com.cloudera.tools.dirutils as dirutils
import com.cloudera.tools.shell as shell


class ScribeInstall(toolinstall.ToolInstall):
  def __init__(self, properties):
    toolinstall.ToolInstall.__init__(self, "Scribe", properties)

    self.addDependency("Hadoop")
    self.addDependency("LogMover")
    self.addDependency("Portal")

    self.hostname = socket.getfqdn()
    self.scribeLogHome = None
    self.scribeUser = SCRIBE_USER
    self.masterHost = None
    self.isScribeStarted = False


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """

  def getScribeLogDir(self):
    """Gets the location where Scribe puts its logs"""
    return self.scribeLogHome

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """


    # Set scribeLogHome; this should not end with a '/'; we trim if it does.
    maybeLogHome = self.properties.getProperty(SCRIBE_LOG_DIR_KEY, \
        SCRIBE_LOG_DIR_DEFAULT)

    if not self.isUnattended():
      self.scribeLogHome = prompt.getString( \
          "Where should Scribe store log files?", maybeLogHome, True)
    else:
      self.scribeLogHome = maybeLogHome

    if self.scribeLogHome == None or len(self.scribeLogHome) == 0:
      raise InstallError("Invalid Scribe log dir; set with --scribe-log-dir")

    # remove any trailing '/' characters.
    while self.scribeLogHome.endswith(os.sep):
      self.scribeLogHome = self.scribeLogHome[0:len(self.scribeLogHome)-1]

    # Determine the hostname for the master scribe server
    if self.isMaster():
      self.masterHost = self.hostname
    else:
      self.masterHost = self.properties.getProperty(SCRIBE_MASTER_ADDR_KEY)
      if not self.isUnattended():
        self.masterHost = prompt.getString( \
            "What is the hostname for the Scribe master server?",
            self.masterHost, True)
      if self.masterHost == None:
        raise InstallError("Scribe master hostname not set; " \
            + "please configure one with --scribe-master")

    # Check the hostname for RFC 1035 compliance.
    if not dnsregex.isIpAddress(self.masterHost) \
        and not dnsregex.isDnsName(self.masterHost):
      raise InstallError("Scribe master hostname is invalid.")


  def installPythonDev(self):
    """ Install python development packages """
    pythonDevPkg = { arch.PACKAGE_MGR_DEBIAN: ["python-dev"],
                     arch.PACKAGE_MGR_RPM: ["python-devel"] }
    self.installPackage(pythonDevPkg)


  def installRuby(self):
    """ Install ruby interpreter """
    rubyPkg = { arch.PACKAGE_MGR_DEBIAN: ["ruby1.8", "ruby1.8-dev"],
                arch.PACKAGE_MGR_RPM: ["ruby", "ruby-devel"] }
    self.installPackage(rubyPkg)

  def installLibEvent(self):
    """ Install libevent library and headers """
    libEventPkg = { arch.PACKAGE_MGR_DEBIAN: ["libevent1", "libevent-dev"],
                    arch.PACKAGE_MGR_RPM: ["libevent", "libevent-devel"] }
    self.installPackage(libEventPkg)

  def getFinalInstallPath(self):
    """ Return the dirname where scribe is installed to. """
    return os.path.join(self.getInstallBasePath(), SCRIBE_INSTALL_SUBDIR)

  def installScribe(self):
    """ Unpack architecture-specific build of scribe. """

    # Figure out which input file we want
    archDetector = arch.getArchDetector()
    archStr = archDetector.getArchString()
    tarballFile = os.path.join(DEPS_PATH, archStr, SCRIBE_PACKAGE)

    # make the output directory
    installPath = self.getFinalInstallPath()
    dirutils.mkdirRecursive(installPath)

    # actually unzip it.
    cmd = "tar -zxf \"" + tarballFile + "\" -C \"" + installPath + "\""
    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error unpacking Scribe (tar returned error)")

    # This contains a sub-tarball full of libraries. Unzip this.
    libTarball = os.path.join(installPath, "scribe_libs.tar.gz")
    libPath = os.path.join(installPath, "lib")
    try:
      dirutils.mkdirRecursive(libPath)
    except OSError, ose:
      raise InstallError("Could not create scribe lib dir.\nReason: %(ose)s" \
          % { "ose" : str(ose) })

    cmd = "tar -zxf \"" + libTarball + "\" -C \"" + libPath + "\""
    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error unpacking Scribe libs (tar returned error)")

    # and now delete the tarball; no longer useful.
    try:
      os.remove(libTarball)
    except OSError:
      raise InstallError("Error removing Scribe library tarball")


  def writeLibWrapper(self):
    """ Set LD_LIBRARY_PATH in a bash script that then runs scribed """

    installPath = os.path.abspath(os.path.join(self.getFinalInstallPath(),
        "lib"))

    fbLibPath = os.path.join(installPath, "fb303")
    boostLibPath = os.path.join(installPath, "boost")
    thriftLibPath = os.path.join(installPath, "thrift")

    ldLibraryPath = fbLibPath + ":" + boostLibPath + ":" + thriftLibPath \
        + ":$LD_LIBRARY_PATH"

    wrapperFileName = os.path.join(self.getFinalInstallPath(), \
        SCRIBE_WRAPPER_NAME)
    output.printlnVerbose("Writing scribe wrapper script to " + wrapperFileName)
    dateStr = time.asctime()

    localConfFile = os.path.join(self.getFinalInstallPath(), \
        "scribe_local.conf")
    masterConfFile = os.path.join(self.getFinalInstallPath(), \
        "scribe_central.conf")

    try:
      handle = open(wrapperFileName, "w")
      handle.write("""#!/bin/bash
#
# Scribe launcher script
# Autogenerated by Cloudera Hadoop Installer %(ver)s
# on %(thedate)s
#
# Usage: %(selfname)s
# Runs the local scribe server
#

bindir=`dirname $0`
bindir=`cd $bindir && pwd`
export LD_LIBRARY_PATH=%(ldLibPath)s

nohup ${bindir}/scribed %(localConfFile)s 2</dev/null </dev/null >/dev/null &
""" % {  "ver"           : DISTRIB_VERSION,
         "thedate"       : dateStr,
         "selfname"      : SCRIBE_WRAPPER_NAME,
         "ldLibPath"     : ldLibraryPath,
         "localConfFile" : localConfFile
      })

      if self.isMaster():
        # Also start the master scribe daemon
        handle.write("""
nohup ${bindir}/scribed %(masterConfFile)s 2</dev/null </dev/null >/dev/null &
""" % { "masterConfFile" : masterConfFile })

      handle.close()
    except IOError, ioe:
      raise InstallError("""
Could not write wrapper script for scribe launcher.
Reason: %(ioe)s
""" %  { "ioe" : str(ioe) })

    cmd = "chmod a+x \"" + wrapperFileName + "\""
    try:
      shell.sh(cmd)
    except shell.CommandError:
      raise InstallError("Could not make scribe launcher executable")


  def createPaths(self):
    """ Create directories for scribe to put log files in """

    dirutils.mkdirRecursive(self.scribeLogHome)
    localDir = os.path.join(self.scribeLogHome, "local")
    dirutils.mkdirRecursive(localDir)

    if self.isMaster():
      masterDir = os.path.join(self.scribeLogHome, "central")
      dirutils.mkdirRecursive(masterDir)

    cmd = "chown -R " + self.scribeUser + " " + self.scribeLogHome
    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error creating directories for scribe")


  def installConfigFiles(self):
    """ Install and rewrite the several config files governing scribe """

    # Copy scribe config files into place
    # And install our variables with sed
    configSource = os.path.abspath(SCRIBE_CONF_INPUTS_PATH)
    configDest = os.path.abspath(self.getFinalInstallPath())

    localConfSrc = os.path.join(configSource, "scribe_local.conf")
    localConfDest = os.path.join(configDest, "scribe_local.conf")

    try:
      cmd = "cp \"" + localConfSrc + "\" \"" + configDest + "\""
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error copying config file for scribe")

    try:
      cmd = 'sed -i -e "s|SCRIBE_LOG_HOME|' + self.scribeLogHome + '|" ' \
          + localConfDest
      shell.sh(cmd)
      cmd = 'sed -i -e "s|SCRIBE_MASTER_HOST|' + self.masterHost + '|" ' \
          + localConfDest
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error rewriting config file for scribe")

    # master server gets scribe_central.conf too.
    if self.isMaster():
      masterConfSrc = os.path.join(configSource, "scribe_central.conf")
      masterConfDest = os.path.join(configDest, "scribe_central.conf")
      try:
        cmd = "cp \"" + masterConfSrc + "\" \"" + configDest + "\""
        shell.sh(cmd)
      except shell.CommandError, ce:
        raise InstallError("Error copying config file for scribe")

      try:
        cmd = 'sed -i -e "s|SCRIBE_LOG_HOME|' + self.scribeLogHome + '|" ' \
            + masterConfDest
        shell.sh(cmd)
      except shell.CommandError, ce:
        raise InstallError("Error rewriting config file for scribe")


    # Configure Hadoop's conf/log4j.xml file:
    # The string "hostname.domain" should be replaced with my fqdn
    # Line 65 "<!--<appender-ref ref="scribe-async"/>-->" should be
    # uncommented.
    hadoopInstaller = toolinstall.getToolByName("Hadoop")
    if hadoopInstaller == None:
      # Really shouldn't get here.
      raise InstallError("Error: Scribe installation depends on Hadoop")

    log4jConfFile = os.path.join(hadoopInstaller.getConfDir(), "log4j.xml")
    try:
      cmd = 'sed -i -e "s/hostname.domain/' + self.hostname + '/" ' \
          + log4jConfFile
      shell.sh(cmd)

      badText = "<!--<appender-ref ref=\"scribe-async\"/>-->"
      goodText = "<appender-ref ref=\"scribe-async\"/>"

      cmd = "sed -i -e 's|" + badText + "|" + goodText + "|' " + log4jConfFile
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error rewriting Hadoop Log4j configuration")




  def install(self):
    """ Run the installation itself. """

    # install dependency packages:
    self.installPythonDev()
    self.installRuby()
    self.installLibEvent()

    self.installScribe()

    # Write a bash script that sets LD_LIBRARY_PATH and invokes scribed
    self.writeLibWrapper()

    self.createPaths()
    self.installConfigFiles()


  def getScribeUserSudo(self):
    """ Return the sudo cmd prefix to launch scribed as the scribe user """

    if self.scribeUser != "root":
      return "sudo -u " + self.scribeUser + " "
    else:
      return ""


  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    self.createInstallSymlink("scribe")
    self.createEtcSymlink("scribe", self.getFinalInstallPath())

    if self.mayStartDaemons():
      # Start scribed.
      output.printlnInfo("Starting local scribe server")
      cmd = os.path.join(self.getFinalInstallPath(), SCRIBE_WRAPPER_NAME)
      try:
        shell.sh(self.getScribeUserSudo() + cmd)
        self.isScribeStarted = True
      except shell.CommandError:
        output.printlnError("Error: Could not start scribed.")


  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify Scribe (0.2)
    pass


  def getRedeployArgs(self):
    """ Provide any command-line arguments to the installer on the slaves """
    args = []

    args.append("--scribe-log-dir")
    args.append(self.scribeLogHome)
    args.append("--scribe-master")
    args.append(self.hostname)

    return args


  def printFinalInstructions(self):
    """ Last instructions to the user on how to finish launching scribe """

    cmd = os.path.join(self.getFinalInstallPath(), SCRIBE_WRAPPER_NAME)
    output.printlnInfo("""
To aggregate logs from all hosts, scribed must be started on each machine.
scribed can be started by running the command:
%(cmd)s

You may wish to put this command in your rc.local file to start scribe on
boot.
""" % { "cmd" : cmd })

    if self.isScribeStarted:
      output.printlnInfo("Note: scribed has already been started.")

