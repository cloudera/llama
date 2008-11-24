# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.toolinstall
#
# Defines the ToolInstall class which is responsible for
# installing a single tool (e.g., "Hadoop", "Hive", etc,
# are separate tools).

class InstallError(Exception):
  """ Errors when running a ToolInstall """

  def __init__(self, value):
    self.value = value

  def __str__(self):
    return repr(self.value)


class ToolInstall(object):
  def __init__(self, toolName):
    self.name = toolName
    self.deps = []

  def getName(self):
    """ The name of the thing to be installed (e.g. "Hadoop") """
    return self.name

  def getDependencies(self):
    """ The other ToolInstall objects which must be run first """
    return self.deps

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



