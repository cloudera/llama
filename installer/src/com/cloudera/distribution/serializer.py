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
# module: com.cloudera.distribution.serializer

"""
    Manages the serialization of toolinstall state to/from disk in between phase/role
    invocations.
"""

import logging

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.roles as roles
import com.cloudera.distribution.toolinstall as toolinstall


def restore_state(filename, role_list, properties):
  """
      Reads the state of the installation/configuration system from the on-disk file
      into the toolinstall objects. Creates any ToolInstall objects necessary to
      hold all the state.

      Returns a list of ToolInstall objects that fulfill the roles you're operating
      on currently (as passed in as a roles list)
  """

  logging.debug("Restoring state from file " + filename)
  logging.debug("Restoring for role list:")
  for role in role_list:
    logging.debug("  role: " + role)

  try:
    handle = open(filename)
    version = handle.readline().strip()
    logging.debug("Got serialization format " + version)
    if len(version) == 0:
      raise InstallError("State file " + filename + " is empty")

    # Read all the serialized data back into the ToolInstall objects
    while True:
      tool_name = handle.readline().strip()
      if len(tool_name) == 0:
        # We've hit the end of the file; no more tools to deserialize.
        logging.debug("End of serialized data")
        break
      else:
        logging.debug("Found record header for tool: " + tool_name)
        tool_obj = toolinstall.make_tool_for_name(tool_name, properties)
        tool_obj.restore_state(handle, role_list, version)
  except IOError, ioe:
    raise InstallError("Error deserializing from file " + filename + ": " + str(ioe))
  finally:
    handle.close()

  # Finally, determine what ToolInstall objects need to be returned to the user.
  # Create them, even if we didn't have anything to deserialize for some of them.

  # This is the set of names of tools to return to the caller. Guaranteed to be
  # deduplicated.
  required_tool_names = roles.get_tools_for_roles(role_list)
  for req_name in required_tool_names:
    logging.debug("Got required tool name: " + req_name)

  # This is the list of associated ToolInstall items.
  required_tools = []

  for tool_name in required_tool_names:
    tool_obj = toolinstall.make_tool_for_name(tool_name, properties)
    required_tools.append(tool_obj)

  return required_tools


def preserve_state(filename):
  """ Preserves the state of all ToolInstall objects in the system so that they
      can be restored for future invocations of portions of the installation
      framework.
  """

  try:
    handle = open(filename, "w")
    handle.write(DISTRIB_VERSION + "\n")
    for tool in toolinstall.getToolList():
      handle.write(tool.getName() + "\n")
      tool.preserve_state(handle)
    handle.close()
  except IOError, ioe:
    raise InstallError("Could not preserve toolinstall state to file " + filename \
        + ": " + str(ioe))


