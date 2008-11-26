# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.toolinstall
#
# Defines the ToolInstall class which is responsible for
# installing a single tool (e.g., "Hadoop", "Hive", etc,
# are separate tools).

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.util.output as output

# map from name (string) -> ToolInstall
toolMap = {}
def getToolByName(name):
  """ Returns the ToolInstall object with a given name."""
  global toolMap
  try:
    return toolMap[name]
  except KeyError:
    return None


class ToolInstall(object):
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

  #### abstract protected interface down here ####
  #### subclasses must implement this ####

  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    output.printlnVerbose("No preconditions for " + self.getName() + "; (ok)")

  def install(self):
    """ Run the installation itself. """
    raise InstallError("Called install() on abstract ToolInstall")

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    raise InstallError("Called configure() on abstract ToolInstall")

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    raise InstallError("Called postInstall() on abstract ToolInstall")

  def verify(self):
    """ Run post-installation verification tests, if configured """
    output.printlnVerbose("No verification tests for " + self.getName() \
      + "; (ok)")


