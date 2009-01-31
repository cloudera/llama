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

import logging

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.util.prompt as prompt


# The list of all legal roles.
# Note that none of these roles are mutually exclusive.
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
  "hive_developer",
  "deployment_master",
  "hive_master",
  "pig_master"
]

# We provide "shorthand" roles that expand into the true roles
# (listed above). The shorthand roles are provided in this map.
__expanding_roles = {
  "master"     : [ "jobtracker", "namenode", "secondary_namenode", "scribe_master",
                   "deployment_master", "hive_master", "pig_master" ],
  "slave"      : [ "datanode", "tasktracker", "scribe_slave" ],
  "developer"  : [ "hadoop_developer", "pig_developer", "hive_developer" ],
  "standalone" : [ "jobtracker", "namenode", "secondary_namenode", "scribe_master",
                   "hive_master", "pig_master", "datanode", "hive_developer",
                   "tasktracker", "scribe_slave", "hadoop_developer", "pig_developer" ]
}


# NOTE(aaron): The scribe master, logmover, and portal all need to run on the same system.
# Thus they have a single role for them all. We could, in the future, divorce the portal
# from this; but this requires developing a fetch protocol from the logmover's output to
# the portal's input.


__role_to_tool = {
  "deployment_master"  : [ "GlobalPrereq" ],
  "jobtracker"         : [ "GlobalPrereq", "Hadoop" ],
  "namenode"           : [ "GlobalPrereq", "Hadoop" ],
  "datanode"           : [ "GlobalPrereq", "Hadoop" ],
  "tasktracker"        : [ "GlobalPrereq", "Hadoop" ],
  "scribe_master"      : [ "GlobalPrereq",  "Hadoop", "Scribe", "Portal", "LogMover" ],
  "scribe_slave"       : [ "GlobalPrereq",  "Scribe" ],
  "secondary_namenode" : [ "GlobalPrereq",  "Hadoop" ],
  "hadoop_developer"   : [ "GlobalPrereq",  "Hadoop" ],
  "pig_developer"      : [ "GlobalPrereq",  "Pig" ],
  "hive_developer"     : [ "GlobalPrereq",  "Hive" ],
  "pig_master"         : [ "GlobalPrereq", "Pig" ],
  "hive_master"        : [ "GlobalPrereq", "Hive" ]
}


def get_role_names():
  """ Return the list of role names """

  global __roles_list

  # make a copy so they can't modify this underlying list.
  out = []
  for role in __roles_list:
    out.append(role)
  return out


def is_basic_role(role_name):
  """ Return True if role_name is a primitive (unexpended) role """
  global __roles_list
  try:
    __roles_list.index(role_name)
    return True
  except ValueError:
    return False


def is_pseudo_role(role_name):
  """ Return True if role_name is a pseudo-role that expands into basic roles """
  global __expanding_roles
  try:
    __expanding_roles[role_name]
    return True
  except KeyError:
    return False

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
    new_tools = get_tools_for_role(role)
    for tool in new_tools:
      try:
        tools.index(tool)
      except ValueError:
        tools.append(tool)

  return tools


def get_roles_from_properties(properties):
  """ Given the installer properties, return the list of roles the user selected.
      If this is an interactive installation, allow the user to select roles if
      none were provided on the command line.

      Reads the ROLES_KEY property for input; sets the $EXPANDED_ROLES_KEY property.
  """

  global __expanding_roles

  user_roles = properties.getProperty(ROLES_KEY)
  if user_roles == None:
    is_unattended = properties.getProperty(UNATTEND_INSTALL_KEY, UNATTEND_DEFAULT)
    if is_unattended:
      # no roles is an irreparable problem.
      raise InstallError("""
I do not know what to install, because you did not select any roles with
the %(arg)s argument. Please restart the installer with this parameter.
You might want to try one of the following settings:
  "%(arg)s master"     - configures the cluster master node
  "%(arg)s slave"      - configures a worker (slave) node
  "%(arg)s developer"  - configures a developer (client) workstation
  "%(arg)s standalone" - configures a standalone machine that acts as its own cluster
""" % { "arg" : ROLES_ARG })
    else:
      # ask the user what roles to use
      logging.info("""
Welcome to the Cloudera Hadoop Distribution installer. This installer
configures a family of programs to operate together on a cluster.
I need to know how this machine interacts with the rest of the cluster;
please tell me the role of this machine by selecting a number from 1--4:
  1) This is the cluster master node
  2) This is a worker (slave) node
  3) This is a developer's machine that connects to a cluster
  4) This is a standalone machine that acts like a complete cluster
  5) (Advanced) Select explicit roles
""")
      initial_role_num = prompt.getInteger("Please enter a number between 1--4", \
          1, 4, None, True)
      if initial_role_num == 1:
        user_roles = "master"
      elif initial_role_num == 2:
        user_roles = "slave"
      elif initial_role_num == 3:
        user_roles = "developer"
      elif initial_role_num == 4:
        user_roles = "standalone"
      elif initial_role_num == 5:
        logging.info("Please enter a comma-delimited list of all roles this machine uses.")
        logging.info("Available roles are:")
        for role in get_role_names():
          logging.info("  " + role)
        user_roles = prompt.getString("Select your installation roles", None, True)
      else:
        # Really shouldn't get here.
        raise InstallError("Trapped invalid role selection: " + str(initial_role_num))

  # at this point, user_roles is a string listing all the roles the user
  # wants to use. Break the string up by commas, strip any whitespace, and
  # expand pseudo-roles into primitive roles.
  user_role_list = user_roles.split(",")
  true_roles = []
  for elem  in user_role_list:
    elem = elem.strip()
    if len(elem) == 0:
      continue
    elif is_basic_role(elem):
      true_roles.append(elem)
    elif is_pseudo_role(elem):
      true_roles.extend(__expanding_roles[elem])
    else:
      raise InstallError("Invalid role: " + elem)

  # Memorize the expanded roles list in the properties.
  properties.setProperty(EXPANDED_ROLES_KEY, true_roles)
  return true_roles


