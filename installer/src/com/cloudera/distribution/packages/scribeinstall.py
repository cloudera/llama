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
# module: com.cloudera.distribution.packages.scribeinstall
#
# Defines the ToolInstall instance that installs Scribe

import logging
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


# how much time do we wait after booting the scribe daemons before
# we return from the startup method? If we go too early, then hadoop
# may miss critical messages. This value is in seconds.
SCRIBE_START_WAIT_TIME = 3


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

    # installing scribe requires being root.
    if self.getCurrUser() != "root":
      raise InstallError("""Error: cannot install scribe without being root.
To install this distribution with scribe, please re-run this installer as root.
To install the distribution without scribe, re-run with the --without-scribe
argument.
""")

    # Check that the scribe user account exists
    try:
      shell.shLines("getent passwd \"" + self.scribeUser + "\"")
      output.printlnVerbose("Found username: " + self.scribeUser)
    except shell.CommandError:
      raise InstallError( \
              "You must create the 'scribe' username to install scribe")

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

    archDetector = arch.getArchDetector()
    if archDetector.getArch() == arch.ARCH_X86_64:
      # Install x86_64-specific ruby libraries on fedora.
      rubyPkg = { arch.PACKAGE_MGR_DEBIAN: ["ruby1.8", "ruby1.8-dev"],
                  arch.PACKAGE_MGR_RPM: ["ruby", "ruby-libs.x86_64"] }
    else:
      rubyPkg = { arch.PACKAGE_MGR_DEBIAN: ["ruby1.8", "ruby1.8-dev"],
                  arch.PACKAGE_MGR_RPM: ["ruby", "ruby-libs"] }
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


  def writeLibWrapper(self, scribeWrapperName, scribeConfFile):
    """ Set LD_LIBRARY_PATH in a bash script that then runs scribed """

    installPath = os.path.abspath(os.path.join(self.getFinalInstallPath(),
        "lib"))

    fbLibPath = os.path.join(installPath, "fb303")
    boostLibPath = os.path.join(installPath, "boost")
    thriftLibPath = os.path.join(installPath, "thrift")

    ldLibraryPath = fbLibPath + ":" + boostLibPath + ":" + thriftLibPath \
        + ":$LD_LIBRARY_PATH"

    wrapperFileName = os.path.join(self.getFinalInstallPath(), \
        scribeWrapperName)
    output.printlnVerbose("Writing scribe wrapper script to " + wrapperFileName)
    dateStr = time.asctime()

    confFile = os.path.join(self.getFinalInstallPath(), scribeConfFile)

    try:
      handle = open(wrapperFileName, "w")
      handle.write("""#!/bin/bash
#
# Scribe launcher script
# Autogenerated by Cloudera Hadoop Installer %(ver)s
# on %(thedate)s
#
# Usage: %(selfname)s
# Runs the scribe server daemon
#

bindir=`dirname $0`
bindir=`cd $bindir && pwd`
export LD_LIBRARY_PATH=%(ldLibPath)s

nohup ${bindir}/scribed %(confFile)s 2</dev/null </dev/null >/dev/null &
""" % {  "ver"           : DISTRIB_VERSION,
         "thedate"       : dateStr,
         "selfname"      : scribeWrapperName,
         "ldLibPath"     : ldLibraryPath,
         "confFile"      : confFile
      })

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

    if self.mayStartDaemons():
      logging.info("Stopping any running scribe daemons...")
      try:
        shell.sh("killall scribed")
        logging.info("ok.")
      except shell.CommandError, ce:
        logging.info("No scribe daemons to stop (OK)")

    # install dependency packages:
    self.installPythonDev()
    self.installRuby()
    self.installLibEvent()

    self.installScribe()

    # Write bash scripts that set LD_LIBRARY_PATH and invoke scribed
    self.writeLibWrapper(SCRIBE_LOCAL_WRAPPER_NAME, "scribe_local.conf")
    self.writeLibWrapper(SCRIBE_CENTRAL_WRAPPER_NAME, "scribe_central.conf")

    self.createPaths()
    self.installConfigFiles()


  def getScribeUserSudo(self):
    """ Return the sudo cmd prefix to launch scribed as the scribe user """

    if self.scribeUser != "root":
      return "sudo -u " + self.scribeUser + " "
    else:
      return ""

  def isDaemonRunning(self):
    """ Returns True if we started scribed. """
    return self.isScribeStarted


  def ensureScribeStarted(self):
    """ Start scribed if it hasn't already been started.
        Refuses to start scribed if we're not allowed to start daemons.
    """

    global SCRIBE_START_WAIT_TIME

    if not self.mayStartDaemons() or self.isDaemonRunning():
      return # nothing to do

    # Start scribed.
    output.printlnInfo("Starting local scribe server")
    scribeLocalCmd = os.path.join(self.getFinalInstallPath(),
      SCRIBE_LOCAL_WRAPPER_NAME)
    scribeCentralCmd = os.path.join(self.getFinalInstallPath(),
      SCRIBE_CENTRAL_WRAPPER_NAME)
    try:
      # run the daemons
      shell.sh(self.getScribeUserSudo() + scribeLocalCmd)

      if self.isMaster():
        shell.sh(self.getScribeUserSudo() + scribeCentralCmd)

      # don't return immediately; give the daemons time to turn on
      # so we don't miss statrup msgs if we start hadoop immediately
      # afterward
      time.sleep(SCRIBE_START_WAIT_TIME)
      self.isScribeStarted = True
    except shell.CommandError:
      output.printlnError("Error: Could not start scribed.")


  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    self.createInstallSymlink("scribe")
    self.createEtcSymlink("scribe", self.getFinalInstallPath())
    self.ensureScribeStarted()


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

    cmd_local = os.path.join(self.getFinalInstallPath(),
        SCRIBE_LOCAL_WRAPPER_NAME)
    cmd_central = os.path.join(self.getFinalInstallPath(),
        SCRIBE_CENTRAL_WRAPPER_NAME)
    output.printlnInfo("""
To aggregate logs from all hosts, scribed must be started on each machine.
scribed can be started by running the following command as user %(scribe)s:
%(cmd_local)s

On the master machine run the following command in addition:
%(cmd_central)s

You may wish to put these commands in your rc.local file to start scribe on
boot.
""" % { "scribe"     : self.scribeUser,
        "cmd_local"   : cmd_local,
        "cmd_central" : cmd_central })

    if self.isDaemonRunning():
      output.printlnInfo("Note: scribed has already been started.")

