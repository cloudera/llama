# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.scpall
#
# scps a local file to a destination on a list of hosts.
# Attempts to provide basic reliability checking.
# This is really just a stop-gap measure until we get something like puppet
# working.
#
# The public interface to this module is the scpMultiHosts() method.


# TODO: Unit test this module.
import threading

import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output

class MultiScpError(Exception):
  """ Errors when running scp to multiple hosts in parallel """
  def __init__(self, failedHostList):
    self.failedHostList = failedHostList

  def getFailedHostList(self):
    return self.failedHostList

  def __str__(self):
    return "Failed hosts: " + repr(self.failedHostList)


class ScpWorker(threading.Thread):
  """ Worker thread that scps files """
  def __init__(self, localFile, user, hostList, remoteFile, properties,
               numRetries):
    threading.Thread.__init__(self)
    self.localFile = localFile
    self.user = user
    self.hostList = hostList
    self.remoteFile = remoteFile
    self.properties = properties
    self.numRetries = numRetries
    self.failedHosts = []

  def getFailedHostList(self):
    """ return the list of hosts for which the scp command failed """
    return self.failedHosts

  def run(self):
    """ keep running until we finish our entire work list. Any entries
        that fail are noted, but we try to finish as many recipients as
        we can. We throw an exn at the end containing the list of bad
        hosts. """

    for host in self.hostList:
      attempt = 0
      success = False
      try:
        while attempt < self.numRetries and not success:
          attempt = attempt + 1
          try:
            shell.scp(self.localFile, self.user, host, self.remoteFile, \
                self.properties)
            success = True
          except shell.SshError, se:
            output.printlnError("Error transfering to " + host)
            output.printlnError(str(se))
            if attempt < self.numRetries:
              output.printlnError("Retrying...")
          except shell.CommandError, ce:
            output.printlnError("Error transfering to " + host)
            output.printlnError(str(ce))
            if attempt < self.numRetries:
              output.printlnError("Retrying...")

        if not success:
          self.failedHosts.append(host)
      except Exception, e:
        output.printlnError("Exception in scp thread: " + str(e))
        self.failedHosts.append(host)



def scpMultiHosts(localFile, user, hostList, remoteFile, properties,
                  numRetries, numParallel):
  """ Will invoke scp from localFile to each entry in hostList
      up to numRetries times. Will use up to numParallel threads to
      accomplish this as quickly as possible. """


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

    worker = ScpWorker(localFile, user, sublist, remoteFile, properties, \
        numRetries)
    workers.append(worker)
    worker.start()

    start = end

  # wait for all these threads to finish up.
  badHosts = []
  for thread in workers:
    thread.join()
    badHosts.extend(thread.getFailedHostList())

  if len(badHosts) > 0:
    raise MultiScpError(badHosts)

