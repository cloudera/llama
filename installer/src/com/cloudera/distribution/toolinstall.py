# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.toolinstall
#
# Defines the ToolInstall class which is responsible for
# installing a single tool (e.g., "Hadoop", "Hive", etc,
# are separate tools).

import os

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError

# alex - the full path isn't used here, because Python
# didn't like it for some reason
import env
# (aaron - This should be com.cloudera.distribution.env)

import com.cloudera.distribution.arch as arch
import com.cloudera.util.output as output
import com.cloudera.tools.shell as shell

# map from name (string) -> ToolInstall
toolMap = {}
def getToolByName(name):
  """ Returns the ToolInstall object with a given name."""
  global toolMap
  try:
    return toolMap[name]
  except KeyError:
    return None


def getToolList():
  """ returns the list of ToolInstall objects """
  global toolMap
  return toolMap.values()


class ToolInstall(object):
  """ Abstract class that defines an installer responsible for configuring
      and installing a single tool.

      These objects may define (during their c'tor phase) the dependencies
      they hold on other tools. Dependencies are indicated by specifying
      the toolName(s) they depend on. The ToolInstall objects must be
      instantiated in-order (it is up to you to define the topological
      sort over the implicit DAG). The dependencies are checked that the
      required prerequisite ToolInstalls already are in the lits.

      The installation process is conducted in 5 stages. These stages are
      applied "horizontally": one phase is applied to all ToolInstall objects
      before the next phase is applied to any of them.

        precheck     - check any system-wide preconditions
        configure    - any configuration for the tool, solicit all user input
        install      - do the installation itself.
          (implicit stage: run any remote deployment here by running the
           installer tool with appropriate --unattend cmdline args on remote
           machines here)
        postInstall  - any final setup stages that depend on everything being
                       installed
        verify       - any unit tests to run on the user system to ensure that
                       everything works right.

      Constructor arguments:
      toolName - the name of the tool (e.g., "Hadoop")
      properties - the global properties object
  """

  def __init__(self, toolName, properties):
    global toolMap

    self.name = toolName
    self.properties = properties
    self.deps = []
    try:
      # make sure a tool with this name doesn't already exist
      toolMap[toolName]
      raise InstallError("ToolInstall already exists for " + toolName)
    except KeyError:
      # good, it doesn't. Add this to the map.
      toolMap[toolName] = self

  #### "private" interface of convenience methods ####

  def addDependency(self, name):
    """ Adds the name of another package which we require as a dep. """
    self.deps.append(name)

  def getProperties(self):
    return self.properties

  def isUnattended(self):
    """ return true if this must be an unattended installation """
    return self.properties.getProperty(UNATTEND_INSTALL_KEY, UNATTEND_DEFAULT)


  def mayStartDaemons(self):
    """ If true, then daemons may be started. otherwise, we are forbidden
        from doing so during installation. This may be because we are creating
        an AMI, have no slave nodes, etc.
    """
    return not self.getProperties().getBoolean(NO_DAEMONS_KEY)


  #### public interface that is part of the base ToolInstall ####

  def getName(self):
    """ The name of the thing to be installed (e.g. "Hadoop") """
    return self.name

  def getDependencies(self):
    """ The names of the other ToolInstall objects which must be run first """
    return self.deps

  def getInstallPrefix(self):
    """ Get the top-level directory where the distribution is installed """
    return getToolByName("GlobalPrereq").getInstallPrefix()

  def getInstallBasePath(self):
    """ Get the directory where individual apps should attach themselves """
    return getToolByName("GlobalPrereq").getAppsPrefix()

  def createInstallSymlink(self, appName):
    "Create a symlink from $INSTALLROOT/appName to the app's install path"

    # create soft links to installed application; remove the
    # existing install symlink if there is one
    linkDest = os.path.join(self.getInstallPrefix(), appName)
    if os.path.exists(linkDest):
      try:
        os.unlink(linkDest)
      except OSError, ose:
        raise InstallError("Cannot remove link " + linkDest + " (" + str(ose) \
            + ")")

    linkSrc = self.getFinalInstallPath()
    try:
      os.symlink(linkSrc, linkDest)
    except OSError, ose:
      raise InstallError("Cannot create link " + linkDest + " (" + str(ose) \
         + ")")

  @staticmethod
  def backupFile(path):
    """
    Given a file path, backup the file by
    first trying to create path.bak.  If that
    exists, then create path.bak.2, then
    path.bak.3, etc.

    Return the file that was created
    """
    extension = ".bak"

    attempt = path + extension
    while os.path.exists(attempt):
      # if this is the first attempt,
      # then just add .1
      if attempt.endswith(extension):
        attempt += ".1"
      # otherwise, break up the list by dots (.),
      # increment the last element, and finally
      # recreate the string
      else:
        parts = attempt.split(".")
        parts[-1] = str(int(parts[-1]) + 1)
        attempt = '.'.join(parts)

    output.printlnInfo("Backing up " + path + " to " + attempt)

    # actually do the copy
    try:
      output.printlnVerbose("Attempting to backup " + path)
      cmd = "cp " + path + " " + attempt
      cpLines = shell.shLines(cmd)
      output.printlnVerbose(cpLines)
    except shell.CommandError:
      raise InstallError("Could not backup " + path)

    return attempt

  def isMaster(self):
    """ Return true if we are installing on a master server, as opposed to
        a slave server."""
    # For the time being, the 'hadoop profile' is a bool true/false for
    # isMaster
    return self.properties.getBoolean(HADOOP_PROFILE_KEY) == PROFILE_MASTER_VAL

  def getCurrUser(self):
    """Returns the user running this installer"""
    cmd = "whoami"
    try:
      whoamiLines = shell.shLines("whoami")
    except shell.CommandError:
      raise InstallError("Could not determine username with 'whoami'")

    if len(whoamiLines) == 0:
      raise InstallError("'whoami' returned no result.")
    return whoamiLines[0].strip()

  def installPackage(self, package_map):
    """
    Installs a list of packages using the package
    manager.  package_map is a dictionary
    that maps the package manager (a
    constant defined in this file) with
    a list of packages (strings)
    This dict doesn't need a package list for
    each package manager.  It will not
    fail if not all packages are defined
    """
    if self.getCurrUser() != "root":
      raise InstallError("This script requires root to install packages")

    arch_inst = arch.getArchDetector()

    # if this is Debian, then set the DEBIAN_FRONTEND
    # env variable so we don't get tailwhip, or the
    # blue screen that doesn't allow for an unattended
    # installation
    if arch_inst.getPackageMgr() == arch.PACKAGE_MGR_DEBIAN:
      os.environ["DEBIAN_FRONTEND"] = "noninteractive"

    pckg_mgr = arch_inst.getPackageMgrBin()

    if pckg_mgr == None:
      raise InstallError("Could not determine your package manager")

    # get the package, but don't break if the user didn't specify
    # a package to include
    pckg_list = []
    try:
      pckg_list =  package_map[arch_inst.getPackageMgr()]
    except:
      return False

    all_installed = True
    for pckg in pckg_list:
      # at the time this was written, we were just considering
      # yum and apt-get.  If you consider something else, make sure
      # it can handle the -y flag
      command = pckg_mgr + " -y install " + pckg

      installLines = shell.shLines(command)

      output.printlnVerbose(installLines)

      exists_msg = ""
      if pckg_mgr == arch.PACKAGE_MGR_DEBIAN:
        exists_msg = "0 upgraded, 0 newly installed"
      elif pckg_mgr == arch.PACKAGE_MGR_RPM:
        exists_msg = "Nothing to do"
      # else case already handled above

      installed = installLines[-1].find(exists_msg) != -1

      output.printlnInfo("Installed " + pckg)

      all_installed = all_installed and installed

    return all_installed

  def modifyDaemon(self, init_map, state):
    """
    Modifies the state of a service.  init_map is a
    map that maps architectures to init scripts.  These
    scripts must have the full path, for example
    "/etc/init.d/mysql".  State is the desired state
    of the service, for example "start," "stop," etc

    The init_map doesn't need to have every single
    architecture either.  This function will not fail
    if an init script is not included for a particular
    architecture.
    """
    if self.getCurrUser() != "root":
      raise InstallError("This script requires root to change init scripts")

    arch_inst = arch.getArchDetector()

    # get the init script, but don't break if the user didn't specify
    # a script
    init_script = ''
    try:
      init_script = init_map[arch_inst.getPlatform()]
    except:
      output.printlnVerbose("Skipping this service")
      return

    try:
      output.printlnVerbose("Attempting to " + state + " " + init_script)

      command = init_script + " " + state

      lines = shell.shLines(command, False)

      output.printlnVerbose(lines)

      output.printlnInfo("Performed a " + state + " on " + init_script)
    except shell.CommandError:
      raise InstallError("Could not " + command + " " + init_script)


  def createEtcSymlink(self, appName, confDir):
    """ Create a symlink from /etc/cloudera/$appName to $confDir """

    # remove the existing symlink first if it exists.

    configDirRoot = getToolByName("GlobalPrereq").getConfigDir()
    configDirDest = os.path.join(configDirRoot, appName)
    if os.path.exists(configDirDest):
      try:
        os.unlink(configDirDest)
      except OSError, ose:
        raise InstallError("Cannot remove link " + configDirDest + " (" \
            + str(ose) + ")")

    try:
      os.symlink(confDir, configDirDest)
    except OSError, ose:
      raise InstallError("Cannot create link " + configDirDest + " (" \
          + str(ose) + ")")


  #### abstract protected interface down here ####
  #### subclasses must implement this ####

  def getFinalInstallPath(self):
    """ Where do we install our tool to? """
    raise InstallError("Called getFinalInstallPath() on abstract ToolInstall")

  def getRedeployArgs(self):
    """ When this installer is invoking itself on another machine, what
        arguments should we pass to the installer for this package?
        Returns a list of strings that should be added to argv when running
        the installer on a remote host """
    return []

  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    output.printlnVerbose("No preconditions for " + self.getName() + "; (ok)")

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    raise InstallError("Called configure() on abstract ToolInstall")

  def install(self):
    """ Run the installation itself. """
    raise InstallError("Called install() on abstract ToolInstall")

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    raise InstallError("Called postInstall() on abstract ToolInstall")

  def verify(self):
    """ Run post-installation verification tests, if configured """
    output.printlnVerbose("No verification tests for " + self.getName() \
      + "; (ok)")

  def printFinalInstructions(self):
    """ If there are any final instructions to the user after installation
        is over, you should print them out in this method. """

    # default: no instructions:
    pass

