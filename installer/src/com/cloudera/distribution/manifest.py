# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.manifest
# Assembles the installation plan based on the user's configuration

from   com.cloudera.distribution.installplan import InstallPlan
from   com.cloudera.distribution.constants import *

from   com.cloudera.distribution.packages.hadoopinstall import HadoopInstall
from   com.cloudera.distribution.packages.hiveinstall import HiveInstall
from   com.cloudera.distribution.packages.piginstall import PigInstall
from   com.cloudera.distribution.packages.scribeinstall import ScribeInstall
from   com.cloudera.distribution.packages.logmoverinstall import LogMoverInstall
from   com.cloudera.distribution.packages.portalinstall import PortalInstall
from   com.cloudera.distribution.packages.globalprereq \
    import GlobalPrereqInstall

import com.cloudera.util.output as output

def createInstallPlan(properties):
  """ Creates the installation plan based on the configured properties """
  plan = InstallPlan(properties)

  # Note that this is an ordered list.
  # The plan will not add an installer if its dependencies are not
  # also in the install list. InstallPlan.addTool() throws an InstallError
  # if this happens.

  plan.addTool(GlobalPrereqInstall(properties))

  if properties.getBoolean(INSTALL_HADOOP_KEY, INSTALL_HADOOP_DEFAULT):
    output.printlnVerbose("Selecting package Hadoop")
    plan.addTool(HadoopInstall(properties))
  if properties.getBoolean(INSTALL_HIVE_KEY, INSTALL_HIVE_DEFAULT):
    output.printlnVerbose("Selecting package Hive")
    plan.addTool(HiveInstall(properties))
  if properties.getBoolean(INSTALL_PIG_KEY, INSTALL_PIG_DEFAULT):
    output.printlnVerbose("Selecting package Pig")
    plan.addTool(PigInstall(properties))
  if properties.getBoolean(INSTALL_SCRIBE_KEY, INSTALL_SCRIBE_DEFAULT):
    output.printlnVerbose("Selecting package Scribe")
    plan.addTool(LogMoverInstall(properties))
    plan.addTool(PortalInstall(properties))
    plan.addTool(ScribeInstall(properties))

  return plan


def getInstallFlags(properties):
  """ Return the list of flags that completely describes the set of tools
      the user has chosen to install or exclude """
  flags = []

  if properties.getBoolean(INSTALL_HADOOP_KEY, INSTALL_HADOOP_DEFAULT):
    flags.append("--install-hadoop")
  else:
    flags.append("--without-hadoop")

  if properties.getBoolean(INSTALL_HIVE_KEY, INSTALL_HIVE_DEFAULT):
    flags.append("--install-hive")
  else:
    flags.append("--without-hive")

  if properties.getBoolean(INSTALL_PIG_KEY, INSTALL_PIG_DEFAULT):
    flags.append("--install-pig")
  else:
    flags.append("--without-pig")

  if properties.getBoolean(INSTALL_SCRIBE_KEY, INSTALL_SCRIBE_DEFAULT):
    flags.append("--install-scribe")
  else:
    flags.append("--without-scribe")

  return flags

