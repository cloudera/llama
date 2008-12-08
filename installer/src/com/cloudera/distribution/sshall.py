# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.sshall
#
# executes a command via ssh on a list of remote hosts
# Attempts to provide basic reliability checking.
# This is really just a stop-gap measure until we get something like puppet
# working.
#
# The public interface to this module is the sshMultiHosts() method.


# TODO: Unit test this module.

import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output


# this "return code" is used in SshResult objects to denote when an SshError
# was thrown by our ssh launcher during execution.
SSH_ERR_STATUS = 255

class SshResult(object):
  """ An object describing the result of running an ssh command.
      Contains the user and hostname used to start the command,
      the return code as provided by ssh, as well as the console
      output of executing the command. """

  def __init__(self, user, host, command):
    self.user = user
    self.host = host
    self.command = command
    self.outLines = []
    self.retStatus = None


  def setOutLines(self, moreOutLines):
    """ appends lines of output to the list of lines of output
        we collected from this command thus far. """
    self.outLines = moreOutLines


  def setStatus(self, status):
    self.retStatus = status


  def getOutput(self):
    """ return all console output lines emitted to the tty by the
        ssh command. """
    return self.outLines

  def getStatus(self):
    """ return the exit status code returned by the ssh commmand """
    return self.retStatus

  def getUser(self):
    return self.user

  def getHost(self):
    return self.host

  def getCommand(self):
    return self.command

  def __str__(self):
    return self.host + "(" + str(self.retStatus) + ")"



class SshWorker(threading.Thread):
  """ Worker thread that performs ssh actions """
  def __init__(self, cmd, user, hostList, properties, numRetries):
    self.cmd = cmd
    self.user = user
    self.hostList = hostList
    self.properties = properties
    self.numRetries = numRetries
    self.resultList = [] # SshResult objects we collect during execution.


  def getResultList(self):
    return self.resultList


  def run(self):
    """ keep running until we finish our entire work list. Any entries
        that fail are noted, but we try to finish as many recipients as
        we can. We create an SshResult object describing each (host, cmd)
        execution result. """

    for host in self.hostList:
      attempt = 0
      success = False
      result = SshResult(self.user, self.host, self.command)
      while attempt < self.numRetries and not success:
        attempt = attempt + 1
        try:
          (lines, ret) = shell.sshLinesAndRet(self.user, self.host, \
              self.command, self.properties)
        except shell.SshError, se:
          result.setStatus(SSH_ERR_STATUS) # we use status
          output.printlnError("Error executing on " + host)
          output.printlnError(str(se))
          if attempt < self.numRetries:
            output.printlnError("Retrying...")
          continue

        result.setOutLines(lines)
        result.setStatus(ret)
        success = (ret == RET_SUCCESS)

        if not success:
          output.printlnError("Error executing on " + host)
          badHosts.append(host)
          if attempt < self.numRetries:
            output.printlnError("Retrying...")

      self.resultList.append(result)



def sshMultiHosts(user, hostList, command, properties,
                  numRetries, numParallel):
  """ Will invoke ssh 'command' to each entry in hostList
      up to numRetries times. Will use up to numParallel threads to
      accomplish this as quickly as possible. Note that we cannot
      easily discern errors in ssh itself from errors from the underlying
      command. Therefore, if numRetries is greater than 1,  it is possible
      that we will successfully use ssh to execute the command, but the
      command itself will return error, causing the command to be reexecuted.
      if numRetries is > 1, then erroneous execution of the command should
      be an idempotent operation on the remote host.

      This command returns a list of SshResult objects."""


  if numParallel < 1:
    numParallel = 1

  workers = []
  start = 0
  split = len(hostList) / numParallel
  for i in range(0, numParallel):
    # figure out what subrange of the host list to use
    end = start + split
    if i == numParallel - 1:
      end = len(hostList)
    sublist = hostList[start:end]

    worker = SshWorker(command, user, sublist, properties, numRetries)
    workers.append(worker)

    start = end

  # wait for all these threads to finish up.
  allResults = []
  for thread in workers:
    thread.join()
    allResults.extend(thread.getResultList)

  return allResults


