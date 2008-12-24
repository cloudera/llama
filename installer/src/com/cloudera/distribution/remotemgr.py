# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.remotemgr
#
# Manages deployment to remote instances

import os
import socket
import sys
import tempfile

from   com.cloudera.distribution.constants import *
from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.manifest as manifest
import com.cloudera.distribution.scpall as scpall
import com.cloudera.distribution.sshall as sshall
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.tools.shell as shell
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
      or hostname == "localhost.localdomain" \
      or hostname == socket.gethostname() \
      or hostname == socket.getfqdn()


def allowLocalhost(properties):
  """ returns True if we're configured to allow redeployment on the localhost
      (this is done just for test-harness purposes) """
  return properties.getBoolean(TEST_MODE_KEY, TEST_MODE_DEFAULT)


def getConfiguredSlaveList(slavesFileName, properties):
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
    if len(line) > 0 and (not isLocalHost(line) or allowLocalhost(properties)):
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
    try:
      hostList.remove(failedHost)
    except ValueError:
      # failedHost wasn't in the hostList; ignore this.
      pass


def doSshAll(user, slaveList, cmd, properties):
  """ perform an sshall for the specified command, on the slaves. Output
      as "verbose" any text returned by the commands. Set the error flag
      if an error was encountered, and remove any failed hosts from the
      slave list. """

  sshResults = sshall.sshMultiHosts(user, slaveList, cmd, properties, \
      NUM_SSH_RETRIES, NUM_SSH_PARALLEL_THREADS)

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
command: %(cmd)s
hosts:""" % { "cmd" : cmd })
    for host in failedHosts:
      output.printlnError("  " + host)
    setRemoteDeployError()
    removeHosts(slaveList, failedHosts)

def doScpAll(localFile, user, slaveList, remoteFile, fileDesc, properties):
  """ perform an scpall for the specified file, to the slaves. set the
      error flag if an error was encountered, and remove any failed hosts
      from the slave list. Print a message to the user if this happens """

  try:
    scpall.scpMultiHosts(localFile, user, slaveList, remoteFile, properties, \
        NUM_SCP_RETRIES, NUM_SCP_PARALLEL_THREADS)
  except scpall.MultiScpError, mse:
    setRemoteDeployError()
    output.printlnError("Unable to send %(desc)s to " % { "desc" : fileDesc } \
        + "the following machines:")
    failedHosts = mse.getFailedHostList()
    for host in failedHosts:
      output.printlnError("  " + str(host))
    removeHosts(slaveList, failedHosts)


def zipInstallerDistribution():
  """ zip up the distribution into a tgz (so we can upload it to the slaves)
      and return the filename where we put it. """

  # Create a uniquely-named temporary file to hold the tgz
  (oshandle, tmpFilename) = tempfile.mkstemp(".tar.gz", "distro-")
  try:
    handle = os.fdopen(oshandle, "w")
    handle.close()
  except OSError:
    # irrelevant because we just want the filename
    pass
  except IOError:
    # irrelevant because we just want the filename
    pass

  # what is the dir we should zip up? calculate that based on the current
  # directory (The installer has chdir'd into the directory where it lives.)
  installerBaseDir = os.path.abspath(os.getcwd())
  distribBaseDir = os.path.abspath(os.path.join( \
      installerBaseDir, DISTRIB_BASE_PATH))

  # run the tar-up command
  output.printlnVerbose("Recompressing distribution for deployment")
  cmd = "tar -czf \"" + tmpFilename + "\" -C \"" + distribBaseDir + "\" ."
  try:
    shell.sh(cmd)
  except shell.CommandError, ce:
    try:
      os.remove(tmpFilename)
    except OSError:
      pass # irrelevant; ok to leave trash behind if it can't be removed.

    raise InstallError("""Could not compress distribution for deployment.
command: %(cmd)s
error: %(ret)s""" % {
        "cmd" : cmd,
        "ret" : str(ce) })

  return tmpFilename


def getRemoteDeployArgs(hadoopSiteFilename, slavesFilename, properties):
  """ return the string of arguments which should be passed to the installer
      when run on the remote deploying end.

      arguments:
        hadoopSiteFilename - the filename on the remote hosts where
                             hadoop-site.xml has been uploaded
        slavesFilename     - the filename on the remote hosts where
                             the slaves list has been uploaded
        properties         - Properties object governing the installer
  """

  argList = []

  argList.append("--unattend")
  argList.append("--as-slave")

  if properties.getBoolean(NO_DAEMONS_KEY):
    argList.append("--no-start-daemons")

  # determine --install / --without for each tool
  toolFlags = manifest.getInstallFlags(properties)
  argList.extend(toolFlags)

  # if we're in testing mode, enable that on the remote host as well
  if properties.getBoolean(TEST_MODE_KEY, TEST_MODE_DEFAULT):
    argList.append("--test-mode")

  if properties.getBoolean(output.VERBOSE_PROP):
    argList.append(output.VERBOSE_FLAG)
  if properties.getBoolean(output.DEBUG_PROP):
    argList.append(output.DEBUG_FLAG)
  if properties.getBoolean(output.QUIET_PROP):
    argList.append(output.QUIET_FLAG)

  # Get any command-line arguments specific to each tool.
  tools = toolinstall.getToolList()
  for tool in tools:
    toolArgs = tool.getRedeployArgs()
    argList.extend(toolArgs)

  # pass along the locations where we stashed the files we generated
  argList.append("--hadoop-site")
  argList.append(hadoopSiteFilename)

  argList.append("--hadoop-slaves")
  argList.append(slavesFilename)

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
  output.printlnDebug("Performing remote deployment")
  output.printlnDebug("Getting global prereq configuration")
  globalPrereqInstaller = toolinstall.getToolByName("GlobalPrereq")

  # get the list of hosts to deploy on
  output.printlnDebug("Loading remote host list")
  slavesFileName = globalPrereqInstaller.getTempSlavesFileName()
  slaveList = getConfiguredSlaveList(slavesFileName, properties)
  output.printlnDebug("Got %(n)i slaves" % { "n" : len(slaveList) })
  if len(slaveList) == 0:
    # There's no sense in executing any of this, since there's nobody to
    # send data to. In addition, various variables we depend on will not
    # have been configured.
    output.printlnDebug("No remote slaves; exiting remote deployment")
    return

  # rezip our distribution up, and get the filename where we put it.
  output.printlnDebug("Compressing installer distribution")
  localFile = zipInstallerDistribution()
  localBaseName = os.path.basename(localFile)
  output.printlnDebug("Created deployment object at " + localFile)

  # figure out where it's going to on the remote ends
  uploadPrefix = globalPrereqInstaller.getUploadPrefix()
  user = globalPrereqInstaller.getUploadUser()
  output.printlnDebug("Uploading to " + uploadPrefix + " as " + user)

  remoteFile = os.path.join(uploadPrefix, localBaseName)

  # mkdir -p the uploadPrefix on all the remote nodes
  output.printlnDebug("Creating remote upload prefix")
  cmd = "mkdir -p \"" + uploadPrefix + "\""
  doSshAll(user, slaveList, cmd, properties)

  # scp the whole package to all the nodes
  output.printlnDebug("Uploading installation package")
  doScpAll(localFile, user, slaveList, remoteFile, "installation packages", \
      properties)

  # now that we're done using the local copy, remove the local tarball
  os.remove(localFile)

  # scp the local hadoop-site file to the remote machines
  # so they can copy it into its final location.
  hadoopInstaller = toolinstall.getToolByName("Hadoop")
  remoteHadoopSite = None
  if hadoopInstaller != None:
    output.printlnDebug("Uploading hadoop-site.xml")
    localHadoopSite = hadoopInstaller.getHadoopSiteFilename()
    remoteHadoopSite = os.path.join(uploadPrefix, "hadoop-site.xml")
    doScpAll(localHadoopSite, user, slaveList, remoteHadoopSite, \
        "hadoop-site.xml", properties)

  # scp the slaves file around
  output.printlnDebug("Uploading common slaves list")
  remoteSlavesName = os.path.join(uploadPrefix, "slaves")
  doScpAll(slavesFileName, user, slaveList, remoteSlavesName, "slaves", \
      properties)


  # Unzip the installation tarball.
  output.printlnDebug("Unzipping installation tarball on remotes")
  cmd = "tar -xzf \"" + remoteFile + "\" -C \"" + uploadPrefix + "\""
  doSshAll(user, slaveList, cmd, properties)

  # come up with the remote install cmd line
  installerFilename = os.path.basename(sys.argv[0])
  prgm = os.path.join(uploadPrefix, INSTALLER_SUBDIR, installerFilename)
  installerArgs = getRemoteDeployArgs(remoteHadoopSite, remoteSlavesName, \
      properties)
  cmd = "\"" + prgm + "\" " + installerArgs
  output.printlnDebug("Remote execution command: " + cmd)


  # Enforce the use of tty's so we can sudo
  origSshOpts = properties.getProperty("ssh.options", "")
  sshOpts = origSshOpts + " -t"
  properties.setProperty("ssh.options", sshOpts)

  # ... here we go - execute that on all nodes
  output.printlnDebug("Executing remote installation program")
  doSshAll(user, slaveList, cmd, properties)
  properties.setProperty("ssh.options", origSshOpts)

  # report status back to the user at the end of this
  if isRemoteDeployError():
    output.printlnError("""
Warning! The distribution was not successfully installed on all hosts. You
may be required to manually install on these hosts before your entire cluster
is operational.""")
  else:
    output.printlnInfo("Successful deployment on %(n)i remote nodes" \
        % { "n" : len(slaveList) })



