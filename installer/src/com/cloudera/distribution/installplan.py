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

import logging

from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.util.output as output


class InstallPlan(object):

  def __init__(self, properties):
    self.properties = properties
    self.unordered_items = []
    self.ordered_items = []


  def addTool(self, tool):
    """ puts the ToolInstall object 'tool' in the list. """

    self.unordered_items.append(tool)


  def __sort_plan(self, all_items):
    """ Ensure that the items do satisfy their constraints. Return a list
        of items that are topologically sorted.
    """

    # TODO(aaron): This is O(n^3); yuck. But |unordered_items| < 8, and we're going to rpms anyway.

    def add_ordered_component(ordered_components, available_components, tool):
      logging.debug("Trying to add " + tool.getName() + " to ordered list.")

      # Check 1: If it's already in the ordered list, don't add it twice.
      try:
        ordered_components.index(tool)
        logging.debug("Skipping redundant item.")
        return # This tool is already in the new list. done.
      except ValueError:
        pass # Not in the new list. proceed.

      # Gather the list of dependencies not present in the ordered list.
      all_deps = tool.getDependencies()
      logging.debug("Existing dependencies: " + str(all_deps))
      for existing_tool in ordered_components:
        existing_tool_name = existing_tool.getName()
        try:
          all_deps.remove(existing_tool_name)
          logging.debug("Satisfied dependency: " + existing_tool_name)
        except ValueError:
          # This tool_name is not yet in the ordered list.
          pass

      # all_deps now contains tool names which are not in the ordered list,
      # but need to be ahead of this tool. If components are available which
      # have those names, add them now.
      for avail_component in available_components:
        component_name = avail_component.getName()
        try:
          all_deps.index(component_name)
          # This component satisfies a dependency.
          logging.debug("Trying to add for dependency: " + component_name)
          add_ordered_component(ordered_components, available_components, avail_component)
          all_deps.remove(component_name)
        except ValueError:
          # This component doesn't satisfy a dependency. Do nothing here.
          pass

      # If all_deps contains anything, it means that we couldn't add components
      # in order to solve the problem.
      if len(all_deps) > 0:
        msg = """Could not satisfy dependencies for %(toolname)s.
The following additional components are required:
""" % { "toolname" : tool.getName() }
        for dep in all_deps:
          msg = msg + "  " + dep + "\n"
        raise InstallError(msg)

      # ok to add this item.
      logging.debug("OK to add to ordered list: " + tool.getName())
      ordered_components.append(tool)

    # Create a new list which is ordered
    new_list = []

    # main loop: put all the unordered items in the ordered list.
    for tool in all_items:
      add_ordered_component(new_list, self.unordered_items, tool)

    logging.debug("Got sorted install plan: " + str(new_list))
    return new_list


  def verify_dependencies(self):
    """ raise InstallError if there are unmet or unsatisfiable dependencies.
        Actually just topo-sorts the new items, if any. That will raise an IE
        if there's a problem.
    """

    if len(self.unordered_items) > 0:
      # We have added items and need to resolve them.
      all_items = []
      all_items.extend(self.ordered_items)
      all_items.extend(self.unordered_items)

      # Do the sort.
      ordered_list = self.__sort_plan(all_items)

      # If we get here, then we're successful. Memoize the sort result.
      self.ordered_items = ordered_list
      self.unordered_items = []


  def getInstallItems(self):
    """ return the list of items to install. Intended to be used
        for iteration. """

    self.verify_dependencies()
    return self.ordered_items

