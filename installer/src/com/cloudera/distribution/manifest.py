# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.manifest
# Assembles the installation plan based on the user's configuration

from   com.cloudera.distribution.installplan import InstallPlan
from   com.cloudera.distribution.installproperties import *

from   com.cloudera.distribution.packages.hadoopinstall import HadoopInstall
from   com.cloudera.distribution.packages.hiveinstall import HiveInstall
from   com.cloudera.distribution.packages.piginstall import PigInstall
from   com.cloudera.distribution.packages.scribeinstall import ScribeInstall
from   com.cloudera.distribution.packages.globalprereq \
    import GlobalPrereqInstall

def createInstallPlan(properties):
  """ Creates the installation plan based on the configured properties """
  plan = InstallPlan(properties)

  # Note that this is an ordered list.
  # The plan will not add an installer if its dependencies are not
  # also in the install list.
  # TODO (aaron): Currently this will throw an InstallError if you try
  #  to install hive or pig without Hadoop. Other options include:
  #  a) catch error and print a warning to the user and continue install
  #  b) catch error, print polite msg, terminate
  #  c) catch error, print polite msg, ask if user wants to add deps,
  #     and continue either way

  plan.addItem(GlobalPrereqInstall(properties))

  if properties.getBoolean(INSTALL_HADOOP_KEY, True):
    output.printlnVerbose("Selecting package Hadoop")
    plan.addItem(HadoopInstall(properties))
  if properties.getBoolean(INSTALL_HIVE_KEY, True):
    output.printlnVerbose("Selecting package Hive")
    plan.addItem(HiveInstall(properties))
  if properties.getBoolean(INSTALL_PIG_KEY, True):
    output.printlnVerbose("Selecting package Pig")
    plan.addItem(PigInstall(properties))
  if properties.getBoolean(INSTALL_SCRIBE_KEY, True):
    output.printlnVerbose("Selecting package Scribe")
    plan.addItem(ScribeInstall(properties))

  return plan

