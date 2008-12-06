# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.globalprereq
#
# Defines the ToolInstall instance that performs the global
# prerequisite configuration
#   e.g., get the user's ssh key file for slave machines, etc.

import os
import tempfile

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.tools.dirutils as dirutils
import com.cloudera.util.output as output
import com.cloudera.util.prompt as prompt


class GlobalPrereqInstall(ToolInstall):
  def __init__(self, properties):
    ToolInstall.__init__(self, "GlobalPrereq", properties)
    self.slavesFileName = None
    self.numSlaves = 0
    self.installPrefix = None
    self.sshKey = None
    self.uploadPrefix = None
    self.uploadUser = None
    self.configDir = None

  def configEtcDir(self):
    """ Determine the dir where we put the links to the config directories """

    maybeConfigDir = self.properties.getProperty(CONFIG_DIR_KEY, \
        CONFIG_DIR_DEFAULT)

    if self.isUnattended():
      self.configDir = maybeConfigDir
    else:
      self.configDir = prompt.getString( \
          "What path should the distribution configuration be in?", \
          maybeConfigDir, True)

    output.printlnVerbose("Setting config dir to " + self.configDir)


  def getConfigDir(self):
    return self.configDir


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
          handle.write(line)
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
      except IOError:
        pass # irrelevant.

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
of these machines. This file should contain one address per line, with no
extra whitespace, blank lines, comments or punctuation. The master
server's address should not be in this file. This will also be used as
the basis for Hadoop's "slaves" file.

If you do not want to install Cloudera Hadoop on some slaves, then omit these
machine addresses for now. You can add these nodes to Hadoop's slaves file
after installation is complete.

Press [enter] to continue.""")

      # Just wait for the user to hit enter.
      raw_input()

      # Run whatever editor the user specified with $EDITOR or --editor
      editorPrgm = self.properties.getProperty(EDITOR_KEY, EDITOR_DEFAULT)
      editorString = editorPrgm + " \"" + tmpFilename + "\""
      ret = os.system(editorString)
      if ret > 0:
        output.printlnError("Encountered error return value from editor")
        raise InstallError("Editor returned " + str(ret))

    # validation: the file shouldn't be empty. It certainly *can*, but
    # that's a very boring Hadoop cluster. We should print a warning msg
    # but continue running.

    # when we're parsing this file, also cache the number of lines
    # in the file

    slaveLines = []
    try:
      handle = open(tmpFilename)
      slaveLines = handle.readlines()
      handle.close()
    except IOError, ioe:
      output.printlnError("Cannot read installer copy of slaves file " \
          + tmpFilename)
      output.printlnError(str(ioe))
      raise InstallError(ioe)

    numSlaves = 0
    for slaveLine in slaveLines:
      if len(slaveLine.strip()) > 0:
        numSlaves = numSlaves + 1
    self.numSlaves = numSlaves
    if self.numSlaves == 0:
      output.printlnInfo( \
"""Warning: Your slaves file appears to be empty. Installation will continue
locally, but your cluster won't be able to do much like this :) You will need
to add nodes to the slaves file after installation is complete.
""")

    # in any case, we've successfully acquired a slaves file. memorize
    # its name.
    self.slavesFileName = tmpFilename

  def getNumSlaves(self):
    return self.numSlaves

  def getTempSlavesFileName(self):
    return self.slavesFileName


  def configInstallPrefix(self):
    """ Determine the root directory where we're going to install this. """

    maybeInstallPrefix = self.properties.getProperty(INSTALL_PREFIX_KEY, \
        INSTALL_PREFIX_DEFAULT)

    if self.isUnattended():
      self.installPrefix = maybeInstallPrefix
    else:
      self.installPrefix = prompt.getString( \
          "What path should the distribution be installed to?", \
          maybeInstallPrefix, True)

    output.printlnVerbose("Installing to " + self.installPrefix)


  def getInstallPrefix(self):
    return self.installPrefix

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
    pass

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
    if self.getNumSlaves() > 0:
      self.configInstallUser()
      self.configSshIdentityKey()
      self.configUploadPrefix()

  def install(self):
    """ Run the installation itself. """

    installDir = self.getAppsPrefix()
    try:
      dirutils.mkdirRecursive(installDir)
    except OSError, ose:
      raise InstallError("Error creating installation directory " \
          + installDir + " (" + str(ose) + ")")

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    # remove the temp slaves file that we created
    slavesFileName = self.getTempSlavesFileName()
    if slavesFileName != None and os.path.exists(slavesFileName):
      os.unlink(slavesFileName)

    # create /etc/cloudera so we can hang symlinks off of it.
    try:
      dirutils.mkdirRecursive(self.getConfigDir())
    except OSError, ose:
      raise InstallError("Error creating configuration directory " \
          + self.getConfigDir() + " (" + str(ose) + ")")

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # This does nothing globally


