# Copyright (c) 2010 Cloudera, inc.
import fnmatch
import os
import subprocess
import sys

class Constants:

  # Cloudera network
  CLOUDERA_NETWORK = '38.102.147.0/24'



def verbose_print(msg='', verbose=True, stream=sys.stdout):
  '''
  Only print if we are not in quiet mode is set

  @param msg Message to display
  @param verbose If not verbose, no message should be displayed
  '''

  if verbose:
    stream.write(msg + "\n")



def display_message(msg, verbose=True, stream=sys.stdout):
  '''
  Nicely format a message in order to make it distinct from random logging

  @param msg Message to display
  @param verbose If not verbose, no message should be displayed
  '''

  verbose_print("\n"+ msg + "\n" + "=" * len(msg) + "\n", verbose, stream=stream)


def recursive_glob(pattern, root=os.curdir):
  '''
  Find all files matching a pattern recursively within root dir

  @param pattern Pattern files must match
  @param root Root directory to search from
  @return List of absolute paths of matched entries
  '''

  for path, dirs, files in os.walk(os.path.abspath(root)):
    for filename in fnmatch.filter(files, pattern):
      yield os.path.join(path, filename)


class SCP:
  '''
  Wrapper around scp command
  '''

  SCP_CMD = "scp"


  def __init__(self, username, host, key=None, options=[]):
    '''
    Constructor

    @param username User name used on the remote location
    @param host Remote host
    @param key SSH Key to use for authentication
    @param options SSH options
    '''

    self.logger = None

    self.username = username
    self.host = host
    self.key = key
    self.options = options

    self.destination_parameter = self.username + '@' + self.host + ':'


  def set_logger(self, logger):
    '''
    Set logger to be used

    @param logger Logger to be used
    '''

    self.logger = logger


  def log(self, message):
    '''
    Logger method. Will log only if a logger is set

    @param message Message to log
    '''

    if self.logger:
      self.logger.info(message)


  def copy(self, source, destination):
    '''
    Copy a file to a remote location

    @param source File to copy
    @param destination Destination
    '''

    cmd = [self.SCP_CMD]
    cmd.extend(self.options)
    if self.key:
      cmd.extend(['-i', self.key])
    cmd.append(source)
    cmd.append(self.destination_parameter + destination)

    self.log("Executing [%s]"%(' '.join(cmd)))

    p = subprocess.Popen(cmd, stderr=subprocess.STDOUT, stdout=subprocess.PIPE)
    p.wait()

    if self.logger:
      for line in p.stdout:
        self.logger.info(line[:-1])

    if p.returncode != 0:
      self.log("Copy of [%s] failed"%(source))
      exit(-1)


class SSH:
  '''
  Wrapper around ssh command
  '''


  SSH_CMD = "ssh"


  def __init__(self, username, host, key=None, options=[]):
    '''
    Constructor

    @param username User name used on the remote location
    @param host Remote host
    @param key SSH Key to use for authentication
    @param options SSH options
    '''

    self.logger = None

    self.username = username
    self.host = host
    self.key = key
    self.options = options


  def set_logger(self, logger):
    '''
    Set logger to be used

    @param logger Logger to be used
    '''

    self.logger = logger


  def log(self, message):
    '''
    Logger method. Will log only if a logger is set

    @param message Message to log
    '''

    if self.logger:
      self.logger.info(message)


  def execute(self, command, parameters=[]):
    '''
    Execute remotely a command

    @param command Command to be executed remotely
    @param parameters Parameters to be passed to the command
    @return Return code
    '''

    # Prepare parameters
    cmd = [self.SSH_CMD]
    if self.key:
      cmd.extend(['-i', self.key])
    cmd.extend(self.options)
    cmd.append(self.username + '@' + self.host)
    cmd.append(command)
    cmd.extend(parameters)

    # Execute it
    self.log("Executing remotely [%s]"%(' '.join(cmd)))
    p = subprocess.Popen(cmd, stderr=subprocess.STDOUT, stdout=subprocess.PIPE)

    # Log output until it's done
    if self.logger:
      for line in p.stdout:
        self.logger.info(line[:-1])
    p.wait()

    self.log("Return code is %s for %s"%(str(p.returncode), self.host))
    return p.returncode

