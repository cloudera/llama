# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.toolinstall
#
# Defines the ToolInstall class which is responsible for
# installing a single tool (e.g., "Hadoop", "Hive", etc, 
# are separate tools). 

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

