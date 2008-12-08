# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.installplan
#
# An InstallPlan is an ordered set of ToolInstall objects
#

from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.util.output as output


class InstallPlan(object):
  def __init__(self, properties):
    self.properties = properties
    self.sortedItems = []

  def addTool(self, tool):
    """ puts the ToolInstall object 'tool' in the list after
        verifying that all its dependencies are present first. """

    deps = tool.getDependencies()
    for dep in deps:
      try:
        output.printlnDebug("Checking dependency: " + dep)
        pos = self.sortedItems.index(toolinstall.getToolByName(dep))
      except ValueError:
        raise InstallError( \
"""Dependency %(dep) required for component %(src)s is not included
in the installation plan.""" %
            { "dep" : str(dep),
              "src" : tool.getName() })

    self.sortedItems.append(tool)


  def getInstallItems(self):
    """ return the list of items to install. Intended to be used
        for iteration. """
    return self.sortedItems

