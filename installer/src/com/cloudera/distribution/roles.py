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
# module: com.cloudera.distribution.roles

"""
    Manages the list of "roles" for installation components and the mapping from roles
    to toolinstall components.
"""

# The list of all legal roles.
__roles_list = [
  "jobtracker",
  "namenode",
  "datanode",
  "tasktracker",
  "scribe_master",
  "scribe_slave",
  "secondary_namenode",
  "hadoop_developer",
  "pig_developer",
  "hive_developer"
]

# NOTE(aaron): The scribe master, logmover, and portal all need to run on the same system.
# Thus they have a single role for them all. We could, in the future, divorce the portal
# from this; but this requires developing a fetch protocol from the logmover's output to
# the portal's input.


__role_to_tool = {
  "jobtracker"         : [ "GlobalPrereq", "Hadoop" ],
  "namenode"           : [ "GlobalPrereq", "Hadoop" ],
  "datanode"           : [ "GlobalPrereq", "Hadoop" ],
  "tasktracker"        : [ "GlobalPrereq", "Hadoop" ],
  "scribe_master"      : [ "GlobalPrereq",  "Hadoop", "Scribe", "Portal", "LogMover" ],
  "scribe_slave"       : [ "GlobalPrereq",  "Scribe" ],
  "secondary_namenode" : [ "GlobalPrereq",  "Hadoop" ],
  "hadoop_developer"   : [ "GlobalPrereq",  "Hadoop" ],
  "pig_developer"      : [ "GlobalPrereq",  "Pig" ],
  "hive_developer"     : [ "GlobalPrereq",  "Hive" ]
}

def get_role_names():
  """ Return the list of role names """

  global __roles_list

  # make a copy so they can't modify this underlying list.
  out = []
  for role in __roles_list:
    out.append(role)
  return out


def get_tools_for_role(role_name):
  """ Return the names of the ToolInstall (e.g., for toolinstall.getToolByName) objects
      that should be created to fulfill a role
  """

  global __role_to_tool

  return __role_to_tool[role_name]


def get_tools_for_roles(role_list):
  """ Given a list of roles, return the set of names of ToolInstall objects to install. Takes care
      of deduplicating this list.
  """

  tools = []
  for role in role_list:
    new_tools = get_tools_for_role(role_list)
    for tool in new_tools:
      try:
        tools.index(tool)
      except ValueError:
        tools.append(tool)


