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
"""Dependency %(dep)s required for component %(src)s is not included
in the installation plan.""" %
            { "dep" : str(dep),
              "src" : tool.getName() })

    self.sortedItems.append(tool)


  def getInstallItems(self):
    """ return the list of items to install. Intended to be used
        for iteration. """
    return self.sortedItems

