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
# module: com.cloudera.distribution.packages.Hiveinstall
#
# Defines the ToolInstall instance that installs Hive

import pickle
import logging
import os
import re
import sys

from   com.cloudera.distribution.constants import *
import com.cloudera.distribution.dnsregex as dnsregex
import com.cloudera.distribution.env as env
import com.cloudera.distribution.hadoopconf as hadoopconf
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.postinstall as postinstall
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.tools.dirutils as dirutils
import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output


class HiveInstall(toolinstall.ToolInstall):
  def __init__(self, properties):
    toolinstall.ToolInstall.__init__(self, "Hive", properties)
    self.addDependency("GlobalPrereq")
    self.addDependency("Hadoop")

    self.hiveParams = {}
    self.hdfsServer = None # "servername:port"


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """
    pass

  def getHadoopInstaller(self):
    hadoopInstaller = toolinstall.getToolByName("Hadoop")
    if None == hadoopInstaller:
      raise InstallError("Hive cannot be installed without Hadoop")
    return hadoopInstaller

  def getHadoopHome(self):
    """ return the value for $HADOOP_HOME, where Hadoop is installed """
    return self.getHadoopInstaller().getFinalInstallPath()


  def getHiveInstallPrefix(self):
    """ return the installation prefix for Hive, underneath the distro-wide
        installation prefix """
    return os.path.join( \
        toolinstall.getToolByName("GlobalPrereq").getAppsPrefix(), \
        HIVE_INSTALL_SUBDIR)

  def getConfDir(self):
    """ Return the dir where all our config files go """
    return os.path.join(self.getHiveInstallPrefix(), "conf")

  def getHiveDefaultFilename(self):
    return os.path.join(self.getConfDir(), "hive-default.xml")

  def getHdfsServer(self):
    return self.hdfsServer

  def configureHdfsServer(self):
    """ Figure out where the HDFS namenode is """

    self.hdfsServer = self.properties.getProperty(HIVE_NAMENODE_KEY)

    # regex for a legal namenode addr
    nameNodeRegex = dnsregex.getDnsNameAndPortRegex()

    if self.hdfsServer == None and not self.isUnattended():
      matchesRegex = False
      while not matchesRegex:
        output.printlnInfo("An HDFS NameNode address has the form: " \
            +"servername:port")
        self.hdfsServer = prompt.getString( \
            "Input the HDFS NameNode DNS address and port for Hive to connect to:", \
            None, True)
        m = nameNodeRegex.match(self.hdfsServer)
        if m != None and m.start() == 0 and m.end() == len(self.hdfsServer):
          matchesRegex = True

    if self.hdfsServer == None:
      raise InstallError("Error: Installing Hive requires the name of " \
          + "an HDFS NameNode\n" \
          + "Please re-run this tool with --namenode set")

    # Check this against the regex.
    m = nameNodeRegex.match(self.hdfsServer)
    if m == None or m.start() != 0 or m.end() != len(self.hdfsServer):
      raise InstallError("Error: NameNode address must match the form " \
          + "address:port")


  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """

    self.configureHdfsServer()

    # Set up the hive-default.xml parameters We need to write out this
    # entire file, but most of these aren't worth changing. We just put
    # the entire list in here, configure what we want to, and write it
    # out during the install process

    self.hiveParams = {}
    self.hiveParams["hive.exec.scratchdir"] = "/tmp/hive-${user.name}"
    # TODO(aaron): 0.2 - set this to false and configure remote derby (CH-70)
    self.hiveParams["hive.metastore.local"] = "true"
    self.hiveParams["javax.jdo.option.ConnectionURL"] = \
        "jdbc:derby:;databaseName=metastore_db;create=true"
    self.hiveParams["javax.jdo.option.ConnectionDriverName"] = \
        "org.apache.derby.jdbc.EmbeddedDriver"
    # TODO(aaron): 0.2 - make this location configurable
    self.hiveParams["hive.metastore.metadb.dir"] = \
        "file://" + HIVE_METADB_DIR
    self.hiveParams["hive.metastore.uris"] = "file://" + HIVE_METADB_DIR
    # CH-150: full URI to warehouse required.
    self.hiveParams["hive.metastore.warehouse.dir"] = \
        "hdfs://" + self.hdfsServer + HIVE_WAREHOUSE_DIR
    self.hiveParams["hive.metastore.connect.retries"] = "5"
    self.hiveParams["hive.metastore.rawstore.impl"] = \
        "org.apache.hadoop.hive.metastore.ObjectStore"
    self.hiveParams["hive.default.fileformat"] = "TextFile"
    self.hiveParams["hive.map.aggr"] = "false"
    self.hiveParams["hive.join.emit.interval"] = "1000"
    self.hiveParams["hive.exec.script.maxerrsize"] = "100000"
    self.hiveParams["hive.exec.compress.output"] = "false"

    # enable intermediate compression if we're going to enable it for MR.
    lzoEnabled = self.getHadoopInstaller().canUseLzo()
    self.hiveParams["hive.exec.compress.intermediate"] = str(lzoEnabled)


  def getFinalInstallPath(self):
    return os.path.join(self.getInstallBasePath(), HIVE_INSTALL_SUBDIR)

  def installHiveTarball(self):
    """ Unzip and install the Hive tarball itself """
    # install Hive by unzipping the package
    hivePackageName = os.path.abspath(os.path.join(PACKAGE_PATH, \
        HIVE_PACKAGE))
    if not os.path.exists(hivePackageName):
      raise InstallError("Missing hive installation package " \
          + hivePackageName)

    installPath = self.getInstallBasePath()
    dirutils.mkdirRecursive(installPath)

    cmd = "tar -zxf \"" + hivePackageName + "\"" \
        + " -C \"" + installPath + "\""

    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error unpacking hive (tar returned error)")

    # And chmod a+x bin/hive (since the newer build script disables this).
    hive_exec_name = os.path.join(installPath, HIVE_INSTALL_SUBDIR, "bin/hive")
    cmd = "chmod a+x \"" + hive_exec_name + "\""
    try:
      shell.sh(cmd)
    except shell.CommandError, ce:
      raise InstallError("Error enabling access to hive shell.")


  def installHiveDefaultConfig(self):
    """ Write the hive-default.xml file out """

    hiveDefaultFilename = self.getHiveDefaultFilename()
    try:
      handle = open(hiveDefaultFilename, "w")
      hadoopconf.writePropertiesHeader(handle)
      hadoopconf.writePropertiesBody(handle, self.hiveParams, [])
      hadoopconf.writePropertiesFooter(handle)
      handle.close()
    except IOError, ioe:
      raise InstallError("Could not write configuration to " \
          + hiveDefaultFilename + "\nReason: " + str(ioe))


  def install(self):
    """ Run the installation itself. """

    if self.has_role("hive_developer"):
      self.installHiveTarball()
      self.installHiveDefaultConfig()


  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """

    if self.has_role("hive_developer"):
      self.createInstallSymlink("hive")
      self.createEtcSymlink("hive", self.getConfDir())

      # must set HADOOP_HOME variable for Hive to work with Hadoop
      hadoopInstallDir = self.getHadoopHome()
      env.addToEnvironment("HADOOP_HOME", hadoopInstallDir)


    if self.has_role("hive_master"):
      # Must create metastore directory in HDFS
      # This is done on the hive "master" machine, which doesn't actually
      # necessarily have Hive installed (but does have Hadoop running);
      # the point is to issue these commands only once.

      hadoopInstaller = toolinstall.getToolByName("Hadoop")
      if hadoopInstaller == None:
        raise InstallError("Hive depends on Hadoop")

      postinstall.add(hadoopInstaller.get_start_hdfs_cmd())

      hadoop_cmdline = hadoopInstaller.get_hadoop_cmdline()
      postinstall.add(hadoop_cmdline + "dfsadmin -safemode wait")
      postinstall.add(hadoop_cmdline + "fs -mkdir " + HIVE_WAREHOUSE_DIR, False)
      postinstall.add(hadoop_cmdline + "fs -mkdir " + HIVE_TEMP_DIR, False)
      postinstall.add(hadoop_cmdline + "fs -chmod a+w " + HIVE_WAREHOUSE_DIR)
      postinstall.add(hadoop_cmdline + "fs -chmod a+w " + HIVE_TEMP_DIR)


  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO(aaron): Verify Hive
    pass


  def getRedeployArgs(self):
    argList = []

    hiveNameNode = self.getHdfsServer()
    if hiveNameNode != None:
      argList.append("--namenode")
      argList.append(hiveNameNode)

    return argList

  def printFinalInstructions(self):
    pass

  def preserve_state(self, handle):
    pmap = {
      "hive_params" : self.hiveParams,
      "hdfs_server" : self.hdfsServer
    }

    pickle.dump(pmap, handle)


  def restore_state(self, handle, role_list, version):
    self.role_list = role_list
    pmap = pickle.load(handle)

    if version == "0.2.0":
      self.restore_0_2_0(pmap)
    else:
      raise InstallError("Cannot read state from file for version " + version)

  def restore_0_2_0(self, pmap):
    """ Read an 0.2.0 formatted pickle map """

    self.hiveParams = pmap["hive_params"]
    self.hdfsSever  = pmap["hdfs_server"]


