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
# module: com.cloudera.distribution.manifest
# Assembles the installation plan based on the user's configuration

import logging

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installplan import InstallPlan
import com.cloudera.distribution.toolinstall as toolinstall

from   com.cloudera.distribution.packages.hadoopinstall import HadoopInstall
from   com.cloudera.distribution.packages.hiveinstall import HiveInstall
from   com.cloudera.distribution.packages.piginstall import PigInstall
from   com.cloudera.distribution.packages.scribeinstall import ScribeInstall
from   com.cloudera.distribution.packages.logmoverinstall import LogMoverInstall
from   com.cloudera.distribution.packages.portalinstall import PortalInstall
from   com.cloudera.distribution.packages.globalprereq import GlobalPrereqInstall

import com.cloudera.util.output as output


def createInstallPlan(tools_list, properties):
  """ Creates the installation plan based on the configured properties """
  plan = InstallPlan(properties)

  # The plan is itself an ordered list of tools.
  for tool_name in tools_list:
    logging.debug("Adding tool: " + tool_name)
    plan.addTool(toolinstall.make_tool_for_name(tool_name, properties))

  return plan


def getInstallFlags(properties):
  """ Return the list of flags that completely describes the set of tools
      the user has chosen to install or exclude.

      This is implemented by translating the roles on the master node
      into the appropriate roles to transmit to the slave nodes.
  """

  flags = []
  out_roles = []

  roles = properties.getProperty(EXPANDED_ROLES_KEY, [])
  for role in roles:
    if role == "jobtracker":
      out_roles.append("tasktracker")
    elif role == "namenode":
      out_roles.append("datanode")
    elif role == "scribe_master":
      out_roles.append("scribe_slave")

  out_role_string = reduce(lambda acc, role: acc + "," + role, out_roles, "")

  flags.append(ROLES_ARG)
  flags.append(out_role_string)
  return flags

