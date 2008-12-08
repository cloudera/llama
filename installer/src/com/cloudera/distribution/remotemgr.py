# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.remotemgr
#
# Manages deployment to remote instances

import os
import sys
import tempfile

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.manifest as manifest
import com.cloudera.distribution.scpall as scpall
import com.cloudera.distribution.sshall as sshall
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.util.output as output

remoteDeployError = False

def setRemoteDeployError():
  "set the flag indicating that there was a problem installing on some hosts"
  global remoteDeployError
  remoteDeployError = True


def isLocalHost(hostname):
  """ return true if the hostname represents localhost """
  return hostname == "127.0.0.1" \
      or hostname == "localhost" \
      or hostname == "localhost.localdomain"


def getConfiguredSlaveList(slavesFileName):
  """ read the slaves file provided by the user at config time, and
      return the list of slave computers on which we should deploy. We filter
      out any references to localhost (as best we can) to avoid installing
      on top of ourself. """

  try:
    handle = open(slavesFileName)
    slaveLines = handle.readlines()
    handle.close()
  except IOError, ioe:
    raise InstallError("""Could not read list of remote hosts to deploy to:
file: %(slavesFile)s
reason: %(ioe)s""" % \
        { "slavesFile" : slavesFileName,
          "ioe"        : str(ioe) })

  slaves = []
  for line in slaveLines:
    line = line.strip()
    if len(line) > 0 and not isLocalHost(line):
      try:
        # test -- is this slave already in the list? if so, ignore it.
        slaves.index(line)
      except ValueError:
        # nope! (good!) add the new slave to the list
        slaves.append(line)

  return slaves


def removeHosts(hostList, failedHosts):
  """ remove all elements of the failed host list from the hostList """
  for failedHost in failedHosts:
    hostList.remove(failedHost)


def doSshAll(user, slaveList, cmd, properties):
  """ perform an sshall for the specified command, on the slaves. Output
      as "verbose" any text returned by the commands. Set the error flag
      if an error was encountered, and remove any failed hosts from the
      slave list. """

  sshResults = sshall.sshMultiHosts(user, slaveList, cmd, properties, \
      NUM_SSH_RETRIES, NUM_SSH_PARALLEL)

  failedHosts = []
  for result in sshResults:
    # print the output text, if any.
    lines = result.getOutput()
    for line in lines:
      output.printlnVerbose(line.rstrip())

    # deal with any error status
    ret = result.getStatus()
    if ret != 0:
      failedHosts.append(result.getHost())

  if len(failedHosts) > 0:
    # tell the user abot the failed hosts, set the failed flag, and remove
    # the hosts from the list of hosts eligible to continue future commands
    output.printlnError( \
"""An error occurred executing a command on remote hosts:
command: %(cmd)
hosts:""" % { "cmd" : cmd })
    for host in failedHosts:
      output.printlnError("  " + host)
    setRemoteDeployError()
    removeHosts(slaveList, failedHosts)


def zipInstallerDistribution():
  """ zip up the distribution into a tgz (so we can upload it to the slaves)
      and return the filename where we put it. """

  # Create a uniquely-named temporary file to hold the tgz
  (oshandle, tmpFilename) = tempfile.mkstemp("", "distro-")
  try:
    handle = os.fdopen(oshandle, "w")
    handle.close()
  except OSError:
    # irrelevant
    pass
  except IOError:
    # irrelevant
    pass

  # what is the dir we should zip up? calculate that based on how we
  # were launched.
  installerBaseDir = os.path.abspath(os.path.dirname(sys.argv[0]))
  distribBaseDir = os.path.abspath(os.path.join( \
      installerBaseDir, DISTRIB_BASE_PATH))

  # run the tar-up command
  output.printlnVerbose("Recompressing distribution for deployment")
  cmd = "tar -czf \"" + tmpFilename + "\" \"" + distribBaseDir + "\""
  try:
    shell.sh(cmd)
  except shell.CommandError, ce:
    try:
      os.remove(tmpFilename)
    except OSError:
      pass # irrelevant

    raise InstallError("""Could not compress distribution for deployment.
command: %(cmd)s
error: %(ret)s""" % {
        "cmd" : cmd,
        "ret" : str(ce) })

  return tmpFilename


def getRemoteDeployArgs(hadoopSiteFilename, properties):
  """ return the string of arguments which should be passed to the installer
      when run on the remote deploying end.

      arguments:
        hadoopSiteFilename - the filename on the remote hosts where
                             hadoop-site.xml has been uploaded
        properties         - Properties object governing the installer
  """

  argList = []

  argList.append("--unattend")
  argList.append("--as-slave")

  # determine --install / --without for each tool
  toolFlags = manifest.getInstallFlags(properties)
  argList.extend(toolFlags)

  # figure out the global configuration settings that guide the
  # remote deployment process
  globalPrereqInstaller = toolinstall.getToolByName("GlobalPrereq")

  # note: currently the remote install prefix is the same as the local one
  installPrefix = globalPrereqInstaller.getInstallPrefix()
  argList.append("--prefix")
  argList.append(installPrefix)

  # wild assumption; java is installed in the same place on the master, slaves
  javaHome = globalPrereqInstaller.getJavaHome()
  argList.append("--java-home")
  argList.append(javaHome)

  # Assuming we're installing Hadoop, configure options specific to Hadoop
  hadoopInstaller = toolinstall.getToolByName("Hadoop")
  if hadoopInstaller != None:
    hadoopMaster = hadoopInstaller.getMasterHost()
    argList.append("--hadoop-master")
    argList.append(hadoopMaster)

    # TODO: Write out the hadoop-site.xml file locally and then upload
    # that to all the slaves.
    argList.append("--hadoop-site")
    argList.append(hadoopSiteFilename)

  # fold down the argument list into a string
  def concat(x, y):
    return x + y

  def quote(s):
    return "\"" + s + "\" "

  return reduce(concat, map(quote, argList), "")




### primary public interface ###

def isRemoteDeployError():
  """ Return true if there was an error deploying to some hosts """
  global remoteDeployError
  return remoteDeployError


def deployRemotes(properties):
  """ Perform setup on remote machines. This will attempt to upload the
      installer package to the remote host, unzip it, and then execute
      everything there. We attempt to perform some basic error handling
      and retries, but nothing too fancy. If any host fails at any stage
      of this process, subsequent stages are skipped on that host. At
      the end of this method, we tell the user where any failures occurred
      so they can manually install on those hosts afterward. """

  errorsPresent = False

  # figure out the global configuration settings that guide the
  # remote deployment process
  globalPrereqInstaller = toolinstall.getToolByName("GlobalPrereq")

  # get the list of hosts to deploy on
  slavesFileName = globalPrereqInstaller.getTempSlavesFileName()
  slaveList = getConfiguredSlaveList(slavesFileName)

  # rezip our distribution up, and get the filename where we put it.
  localFile = zipInstallerDistribution()
  localBaseName = os.path.basename(localFile)

  # figure out where it's going to on the remote ends
  uploadPrefix = globalPrereqInstaller.getUploadPrefix()
  user = globalPrereqInstaller.getUploadUser()

  remoteFile = os.path.join(uploadPrefix, localBaseName)

  # mkdir -p the uploadPrefix on all the remote nodes
  cmd = "mkdir -p \"" + uploadPrefix + "\""
  doSshAll(user, slaveList, cmd, properties)

  # scp the whole package to all the nodes
  try:
    scpall.scpMultiHosts(localFile, user, slaveList, remoteFile, properties, \
        NUM_SCP_RETRIES, NUM_SCP_PARALLEL_THREADS)
  except scpall.MultiScpError, mse:
    setRemoteDeployError()
    output.printlnError("Unable to send installation packages to " \
        + "the following machines:")
    failedHosts = mse.getFailedHostList()
    for host in failedHosts:
      output.printlnError("  " + str(host))
    removeHosts(slaveList, failedHosts)

  # now that we're done using the local copy, remove the local tarball
  os.remove(localFile)

  # Unzip the installation tarball.
  cmd = "tar -xzf \"" + remoteFile + "\" -C \"" + uploadPrefix + "\""
  doSshAll(user, slaveList, cmd, properties)

  # come up with the remote install cmd line
  installerFilename = os.path.basename(sys.argv[0])
  prgm = os.path.join(uploadPrefix, INSTALLER_SUBDIR, installerFilename)
  cmd = "\"" + prgm + "\" " + getRemoteDeployArgs(properties)

  # ... here we go - execute that on all nodes
  doSshAll(user, slaveList, cmd, properties)

  # report status back to the user at the end of this
  if isRemoteDeployError():
    output.printlnError("""
Warning! The distribution was not successfully installed on all hosts. You
may be required to manually install on these hosts before your entire cluster
is operational.""")
  else:
    output.printlnInfo("Successful deployment on %(n)i remote nodes" \
        % { "n" : len(slaveList) })



