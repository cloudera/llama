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
    Installs a package using the package
    manager.  package_map is a dictionary
    that maps the package manager (a
    constant defined in this file) with
    the name of the package (a string)
    """
    if self.getCurrUser() != "root":
      raise InstallError("This script requires root to install packages")    

    arch_inst = arch.getArchDetector()

    pckg_mgr = arch_inst.getPackageMgrBin()
    pckg =  package_map[arch_inst.getPackageMgr()]

    if pckg_mgr == None:
      raise InstallError("Could not determine your package manager")

    command = pckg_mgr + " -y install " + pckg

    installLines = shell.shLines(command)

    output.printlnInfo("Installed " + pckg)

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
        Returns a list of strings """
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


