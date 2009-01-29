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
# module: com.cloudera.distribution.packages.globalprereq
#
# Defines the ToolInstall instance that performs the global
# prerequisite configuration
#   e.g., get the user's ssh key file for slave machines, etc.

import pickle
import logging
import os
import tempfile

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.dnsregex import *
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.java as java
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.tools.dirutils as dirutils
import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output
import com.cloudera.util.prompt as prompt


class GlobalPrereqInstall(ToolInstall):
  def __init__(self, properties):
    ToolInstall.__init__(self, "GlobalPrereq", properties)
    self.slavesFileName = None
    self.numSlaves = 0
    self.installPrefix = None
    self.remotePrefix = None
    self.sshKey = None
    self.uploadPrefix = None
    self.uploadUser = None
    self.configDir = None
    self.javaHome = None

  def precheckJava(self):
    """ Check that Java 1.6 is installed """
    # We have to check for Sun Java 1.6
    javaHome = java.getJavaHome(self.properties)
    if self.isUnattended():
      if javaHome == None:
        output.printlnError( \
           """JAVA_HOME is not set, and the Java installation path was not set
  with --java-home. Please restart the installer with this configured.""")
        raise InstallError("Could not find compatible JAVA_HOME")
      else:
        output.printlnVerbose("Using JAVA_HOME of " + javaHome)
    else:
      # confirm that our value for JAVA_HOME is correct.
      # If the user didn't specify one, try to look for a reasonable value
      if javaHome == None:
        javaHomeGuess = java.guessJavaHome(self.properties)
      else:
        javaHomeGuess = javaHome

      javaHome = java.getJavaHomeFromUser(javaHomeGuess)

    # now that we have a value for JAVA_HOME, assert that we can find
    # Java there.
    while not java.canFindJDK(javaHome, self.properties):
      output.printlnError("An invalid JAVA_HOME was specified; " \
          + "this must point to Sun Java 1.6")

      if self.isUnattended():
        # Nothing to do but give up
        raise InstallError("Could not find compatible JAVA_HOME")
      else:
        # Ask the user for a better value for JAVA_HOME.
        javaHome = getJavaHomeFromUser(javaHome)

    self.javaHome = javaHome


  def getJavaHome(self):
    """ Return the value for JAVA_HOME we solicited and verified """
    return self.javaHome

  def configEtcDir(self):
    """ Determine the dir where we put the links to the config directories """

    if self.getCurrUser() == "root":
      config_dir = CONFIG_DIR_DEFAULT
    else:
      config_dir = os.path.expanduser("~/etc")

    maybeConfigDir = self.properties.getProperty(CONFIG_DIR_KEY, config_dir)
    maybeConfigDir = os.path.abspath(os.path.expanduser(maybeConfigDir))

    if self.isUnattended():
      self.configDir = maybeConfigDir
    else:
      self.configDir = prompt.getString( \
          "What path should the distribution configuration be in?", \
          maybeConfigDir, True)

    output.printlnVerbose("Setting config dir to " + self.configDir)


  def getConfigDir(self):
    return self.configDir

  def editSlavesFile(self, filename):
    """ Launches the editor for the slaves file in interactive mode.
        Called by configSlavesFile"""

    logging.debug("Editing slaves file: " + filename)

    # rewrite the file to contain the boilerplate text at the top
    try:
      handle = open(filename)
      slave_lines = handle.read()
      handle.close()

      handle = open(filename, "w")
      handle.write(self.get_slaves_file_boilerplate())
      handle.write(slave_lines)
      handle.close()
    except IOError, ioe:
      raise InstallError("Could not open the slaves file " + filename + " for editing: " + str(ioe))

    # Run whatever editor the user specified with $EDITOR or --editor
    editorPrgm = self.properties.getProperty(EDITOR_KEY, EDITOR_DEFAULT)
    editorString = editorPrgm + " \"" + filename + "\""
    ret = os.system(editorString)
    if ret > 0:
      output.printlnError("Encountered error return value from editor")
      raise InstallError("Editor returned " + str(ret))


  def validateSlavesFile(self, filename):
    """ Reads the slaves file after it has been edited (or reads the one
        supplied on the command line), determines if it is valid or not.
        In interactive mode, allows the user to go back and edit the file
        if errors were detected.
    """

    logging.debug("Validating slaves file: " + filename)

    # validation: the file shouldn't be empty. It certainly *can*, but
    # that's a very boring Hadoop cluster. We should print a warning msg
    # but continue running.

    # when we're parsing this file, also cache the number of lines
    # in the file

    slaveLines = []
    try:
      handle = open(filename)
      slaveLines = handle.readlines()
      handle.close()
    except IOError, ioe:
      output.printlnError("Cannot read installer copy of slaves file: " \
          + filename)
      output.printlnError(str(ioe))
      raise InstallError(ioe)

    # parse the slave lines we just read. Also, rewrite this file,
    # removing any empty lines or lines starting with '#'
    foundIpAddr = False
    foundBogusAddr = False

    try:
      handle = open(filename, "w")
      numSlaves = 0
      for slaveLine in slaveLines:
        slaveLine = slaveLine.strip()
        if len(slaveLine) == 0 or slaveLine[0] == "#":
          continue
        else:
          if isIpAddress(slaveLine):
            foundIpAddr = True
          elif not isDnsName(slaveLine):
            foundBogusAddr = True
          handle.write(slaveLine + "\n")
          numSlaves = numSlaves + 1
      handle.close()
    except IOError, ioe:
      raise InstallError("Error: Couldn't rewrite slaves file. Reason: " \
          + str(ioe))

    self.numSlaves = numSlaves
    allow_reedit = False
    if self.numSlaves == 0:
      output.printlnInfo( \
"""Warning: Your slaves file appears to be empty. Installation will continue
locally, but your cluster won't be able to do much like this :) You will need
to add nodes to the slaves file after installation is complete.
""")
      allow_reedit = True
    elif foundBogusAddr:
      output.printlnInfo( \
"""Warning: Your slaves file appears to contain lines which are not valid DNS
addresses.""")
      allow_reedit = True
    elif foundIpAddr:
      output.printlnInfo( \
"""Warning: Your slaves file appears to contain IP addresses. If you enable
a DFS hosts file, this may prevent slave nodes from participating in your
cluster. To correct this problem, change the IP addresses in the list to the
fully-qualified hostnames of the same machines.""")
      allow_reedit = True

    if allow_reedit and not self.isUnattended():
      do_reedit = prompt.getBoolean( \
          "Do you want to edit the slaves file to correct this?", True, True)
      if do_reedit:
        self.editSlavesFile(filename)
        self.validateSlavesFile(filename) # tail recurse for this

  def get_slaves_file_boilerplate(self):
    """ return a string that should be put in an 'empty' slaves file to give instructions
        to the user about what to do in this file.
    """

    return \
"""# Fill this file with the addresses of the slave nodes you want to install
# on. These must be the fully-qualified DNS addresses of the machines.
# good: slave0.foocorp.com
# bad: slave0
# bad: 10.4.100.1
#
# Put one address on each line of this file.
# Lines beginning with '#' are ignored, as are empty lines.
"""

  def configSlavesFile(self):
    """ Configure the slaves file. This involves calling up an editor
        for the user to use if we're in interactive mode. This is
        used as the input to the hadoop 'slaves' file as well as the
        list of machines that we should deploy the distribution to. """

    # get a temp file name for us to put the slaves file contents into.
    (oshandle, tmpFilename) = tempfile.mkstemp("", "slaves-")
    handle = os.fdopen(oshandle, "w")

    # check: did the user also provide a slaves file from the start?
    userSlavesFile = self.properties.getProperty(HADOOP_SLAVES_FILE_KEY)
    userSlavesSuccess = False
    if userSlavesFile != None:
      # we have a user's slaves file. Copy this into the temp file
      # read their data in first....
      userSlavesFile = os.path.join(self.properties.getProperty(BASE_DIR_KEY), userSlavesFile)
      try:
        userFileHandle = open(userSlavesFile)
        userSlaveLines = userFileHandle.readlines()
        userFileHandle.close()
      except IOError, ioe:
        output.printlnError("Could not open provided slaves file: " \
            + userSlavesFile)
        output.printlnError(str(ioe))

      # now write ours out
      try:
        for line in userSlaveLines:
          line = line.strip()
          if len(line) == 0 or line[0] == "#":
            # ignore empty lines or comments.
            continue
          else:
            handle.write(line + "\n")
        handle.close()
        userSlavesSuccess = True
      except IOError, ioe:
        output.printlnError("Unexpected error copying slave file contents")
        output.printlnError(ioe)
    else:
      # don't need the open file handle associated with this file
      # (we'll need to reopen it after the user edits it)
      try:
        handle.close()
      except IOError, ioe:
        pass # no reason to worry here. we didn't actually do anything.

    if self.isUnattended():
      # if we don't have a user slaves file, bail.
      if userSlavesFile == None:
        output.printlnError("No slaves file specified with --hadoop-slaves")
        output.printlnError("You must specify a file (even if it is empty)")
        raise InstallError("No slaves file specified")
      elif not userSlavesSuccess:
        # the reading/writing of this file was problematic.
        output.printlnError("Error accessing user slaves file")
        raise InstallError("Error accessing user slaves file")
    else:
      # interactive mode. Give them a chance to edit this file.

      output.printlnInfo("""
You must now provide the addresses of all slaves which are part of this
cluster. This installer will install the Cloudera Hadoop distribution to all
of these machines. This file should contain one address per line. You may
use blank lines; lines beginning with '#' will be ignored.

All addresses must be fully-qualified DNS addresses. e.g., slave1.foocorp.com
Do not use IP addresses (e.g., "10.1.100.1") or hostnames (e.g., "slave1").

The master server's address should not be in this file. This will also be
used as the basis for Hadoop's "slaves" file, and (if configured) the DFS hosts
file.

If you do not want to install Cloudera Hadoop on some slaves, then omit these
machine addresses for now. You can add these nodes to Hadoop's slaves file
after installation is complete.

Press [enter] to continue.""")

      # Just wait for the user to hit enter, then launch the editor.
      raw_input()
      self.editSlavesFile(tmpFilename)

    # Validate the contents of the slaves file. If we're in interactive mode,
    # give the user the chance to go back and edit the file until all errors
    # are rectified. When this returns, we have addresses that either we are
    # happy with, or the user has overriden the check.
    self.validateSlavesFile(tmpFilename)

    # we've successfully acquired a slaves file. memorize its name.
    self.slavesFileName = tmpFilename
    output.printlnDebug("Slaves input file: " + self.slavesFileName)


  def getNumSlaves(self):
    return self.numSlaves

  def getTempSlavesFileName(self):
    return self.slavesFileName


  def configInstallPrefix(self):
    """ Determine the root directory where we're going to install this. """

    if self.getCurrUser() == "root":
      install_default = INSTALL_PREFIX_DEFAULT
    else:
      install_default = os.path.expanduser("~/cloudera")

    maybeInstallPrefix = self.properties.getProperty(INSTALL_PREFIX_KEY, \
        install_default)

    if self.isUnattended():
      self.installPrefix = maybeInstallPrefix
    else:
      self.installPrefix = prompt.getString( \
          "What path should the distribution be installed to?", \
          maybeInstallPrefix, True)
      self.installPrefix = os.path.abspath(os.path.expanduser(self.installPrefix))

    if self.properties.getBoolean(TEST_MODE_KEY, TEST_MODE_DEFAULT):
      # we allow installation to a separate prefix on remote hosts in testing
      maybeRemotePrefix = self.properties.getProperty(REMOTE_PREFIX_KEY, \
          self.installPrefix)
      if self.isUnattended():
        self.remotePrefix = maybeRemotePrefix
      else:
        self.remotePrefix = prompt.getString( \
            "What remote path should the distribution install to?", \
            maybeRemotePrefix, True)
    else:
      # set the remote installation prefix to match.
      self.remotePrefix = self.installPrefix

    output.printlnVerbose("Installing to " + self.installPrefix)
    output.printlnVerbose("Remote install to " + self.remotePrefix)


  def getInstallPrefix(self):
    return self.installPrefix

  def getRemoteInstallPrefix(self):
    return self.remotePrefix

  def getAppsPrefix(self):
    """ Where under the install prefix do the actual app packages go? """
    return os.path.join(self.getInstallPrefix(), APP_SUBDIR)

  def configSshIdentityKey(self):
    """ Determine where the id_{d|r}sa key is that we should use to
        access all the slaves when we log in there. """
    maybeSshKey = self.properties.getProperty(SSH_IDENTITY_KEY)
    if maybeSshKey == None:
      userDir = os.path.expanduser("~")
      userSshDir = os.path.join(userDir, ".ssh")
      rsaKey = os.path.join(userSshDir, "id_rsa")
      dsaKey = os.path.join(userSshDir, "id_dsa")
      if os.path.exists(rsaKey):
        output.printlnVerbose("Trying default rsa key: " + rsaKey)
        maybeSshKey = rsaKey
      elif os.path.exists(dsaKey):
        output.printlnVerbose("Trying default dsa key: " + dsaKey)
        maybeSshKey = dsaKey

    if not self.isUnattended():
      maybeSshKey = prompt.getString("What SSH key should this installer " \
          + "use to access slave nodes for deployment?\n", maybeSshKey, False)

    if maybeSshKey != None and not os.path.exists(maybeSshKey):
      output.printlnInfo("Warning: provided ssh key " + maybeSshKey \
           + " does not exist; ignoring")
      maybeSshKey = None

    if self.isUnattended() and maybeSshKey == None:
      output.printlnError("Error: Cannot deploy to slaves without an " \
          + "ssh key in unattended mode.")
      output.printlnError("You must provide one with --identity")
      raise InstallError("No ssh key provided")

    self.sshKey = maybeSshKey

    if self.isUnattended():
      # ensure that we don't attempt to use a password if the key fails.
      # 'ssh.options' is respected by shell.ssh*(); set this property
      # here so that it gets carried through to the uses of ssh later.
      sshOpts = self.properties.getProperty("ssh.options", "")
      sshOpts = sshOpts + " -o PasswordAuthentication=no"
      self.properties.setProperty("ssh.options", sshOpts)


  def getSshKey(self):
    """ Return the ssh key (if any) to be used to ssh/scp to the slave nodes.
        If the user is in unattended mode, this will definitely be a file.
        If its interactive, this might be None. In which case, the user is
        going to enter their password... a lot."""

    return self.sshKey


  def configUploadPrefix(self):
    """ Determine where we should upload packages to on the slave nodes;
        This is our "beachhead" from which we install everything else """

    maybeUploadPrefix = self.properties.getProperty(UPLOAD_PREFIX_KEY, \
        UPLOAD_PREFIX_DEFAULT)
    maybeUploadPrefix = os.path.abspath(os.path.expanduser(maybeUploadPrefix))

    if not self.isUnattended():
      maybeUploadPrefix = prompt.getString("When uploading installation " \
          + "files to slaves, where can I put them?\n", maybeUploadPrefix, True)

    output.printlnVerbose("Setting upload prefix to " + maybeUploadPrefix)
    self.uploadPrefix = maybeUploadPrefix


  def getUploadPrefix(self):
    """ Return the path on remote nodes where we can upload packages """
    return self.uploadPrefix


  def configInstallUser(self):
    """ Determine what username we should use to log in to the remote
        machines with. """

    maybeUploadUser = self.properties.getProperty(SSH_USER_KEY)

    if maybeUploadUser == None:
      maybeUploadUser = os.getenv("USER")
      if maybeUploadUser == None:
        # How can $USER not be set? Oh well, go for the gold.
        maybeUploadUser = "root"

    if not self.isUnattended():
      maybeUploadUser = prompt.getString( \
         "When installing on slaves, what user do I log in as?", \
         maybeUploadUser, True)

    output.printlnVerbose("Setting upload user to " + maybeUploadUser)
    self.uploadUser = maybeUploadUser


  def getUploadUser(self):
    """ return the user to log in as on the remote nodes """
    return self.uploadUser


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """

    # Any globally-required prerequisites are checked here.
    self.precheckJava() # Java 1.6 is one of these.

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet. """

    # Any global config settings are retrieved here
    # - install prefix (/usr/share/cloudera)
    # - remote slave machines to install on
    # - ssh key for installation user on all slave machines
    # - installation username on all slave machines
    # - upload prefix on slave machines (e.g., /tmp/)
    self.configInstallPrefix()
    self.configEtcDir()
    self.configSlavesFile()
    if self.getNumSlaves() > 0 and self.isMaster():
      self.configInstallUser()
      self.configSshIdentityKey()
      self.configUploadPrefix()


  def install(self):
    """ Run the installation itself. """

    # Create the apps dir
    installDir = self.getAppsPrefix()
    try:
      logging.debug("Creating installation directory: " + installDir)
      dirutils.mkdirRecursive(installDir)
    except OSError, ose:
      raise InstallError("Error creating installation directory " \
          + installDir + " (" + str(ose) + ")")

    # Create the docs dir and copy all the documentation there.
    docsDest = os.path.join(self.getInstallPrefix(), DOCS_SUBDIR)
    if not docsDest.endswith(os.sep):
      docsDest = docsDest + os.sep

    try:
      logging.debug("Creating documentation directory: " + docsDest)
      dirutils.mkdirRecursive(docsDest)
    except OSError, ose:
      raise InstallError("Error creating docs directory " + docsDest \
          + " (" + str(ose) + ")")

    docsInputDir = os.path.abspath(DOCS_INPUT_PATH)
    if not docsInputDir.endswith(os.sep):
      docsInputDir = docsInputDir + os.sep

    cmd = "cp -R " + docsInputDir + "* \"" + docsDest + "\""
    try:
      shell.sh(cmd)
    except shell.CommandError:
      raise InstallError("Error installing documentation")


  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    # create /etc/cloudera so we can hang symlinks off of it.
    try:
      dirutils.mkdirRecursive(self.getConfigDir())
    except OSError, ose:
      raise InstallError("Error creating configuration directory " \
          + self.getConfigDir() + " (" + str(ose) + ")")

  def verify(self):
    """ Run post-installation verification tests, if configured """

    # This does nothing globally
    pass

  def getRedeployArgs(self):
    argList = []
    installPrefix = self.getRemoteInstallPrefix()
    argList.append("--prefix")
    argList.append(installPrefix)

    argList.append("--config-prefix")
    argList.append(self.getConfigDir())

    # assume java is installed in the same place on the master, slaves
    javaHome = self.getJavaHome()
    argList.append("--java-home")
    argList.append(javaHome)

    return argList

  def preserve_state(self, handle):
    pmap = {
      "slaves_file_name" : self.slavesFileName,
      "num_slaves"       : self.numSlaves,
      "install_prefix"   : self.installPrefix,
      "remote_prefix"    : self.remotePrefix,
      "ssh_key"          : self.sshKey,
      "upload_prefix"    : self.uploadPrefix,
      "upload_user"      : self.uploadUser,
      "config_dir"       : self.configDir,
      "java_home"        : self.javaHome
    }

    pickle.dump(handle, pmap)

  def restore_state(self, handle, role_list, version):
    self.role_list = role_list
    pmap = pickle.load(handle)

    if version == "0.2.0":
      self.restore_0_2_0(pmap)
    else:
      raise InstallError("Cannot read state from file for version " + version)

  def restore_0_2_0(self, pmap):
    """ Read an 0.2.0 formatted pickle map """

    self.slavesFileName = pmap["slaves_file_name"]
    self.numSlaves      = pmap["num_slaves"]
    self.installPrefix  = pmap["install_prefix"]
    self.remotePrefix   = pmap["remote_prefix"]
    self.sshKey         = pmap["ssh_key"]
    self.uploadPrefix   = pmap["upload_prefix"]
    self.uploadUser     = pmap["upload_user"]
    self.configDir      = pmap["config_dir"]
    self.javaHome       = pmap["java_home"]


