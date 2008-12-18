# (c) Copyright 2008 Cloudera, Inc.
#
# module: distrotester.setup.platformsetup
#
# installs necessary components on top of the base installation
# of some platform. This code is run on the newly-provisioned EC2 instance.
# It is assumed that we have root privileges when we execute this, and
# carte blanche to change the system's layout at will.
#
# This is an abstract interface module

import os

import com.cloudera.tools.dirutils as dirutils
import com.cloudera.tools.shell as shell

from   distrotester.constants import *


class PlatformSetup(object):

  def __init__(self, properties):
    self.properties = properties

  ### public abstract interface; subclasses must implement these ###

  def remoteBootstrap(self):
    """ Returns a list of commands (one per string) to run via ssh, as root
        on the instance to start the setup process. This can do things like
        ensure that python is installed. e.g., [ "yum install python" ] """

    # default implementation does nothing.
    return []

  def setup(self):
    """ Perform platform-specific setup actions; executed on the remote
        instance after the distrotester has been uploaded and unzipped
    """

    # default implementation does nothing.
    pass


  ### methods which provide useful functionality to subclasses

  def wget(self, url, destFile):
    """ wgets a url to a file """
    shell.sh("wget '" + url + "' -O \"" + destFile + "\"")


  def s3get(self, bucket, key, targetFile):
    """ Uses s3cmd.rb to retrieve an object specified by bucket:key and
        places it in targetFile """

    # Make sure the environment is set right for this cmd

    accessKeyId = self.properties.getProperty(AWS_ACCESS_KEY_ID)
    secretKey = self.properties.getProperty(AWS_SECRET_ACCESS_KEY)
    accountId = self.properties.getProperty(AWS_ACCOUNT_ID_KEY)

    if accessKeyId != None:
      os.environ[AWS_ACCESS_KEY_ENV] = accessKeyId

    if secretKey != None:
      os.environ[AWS_SECRET_KEY_ENV] = secretKey

    if accountId != None:
      os.environ[AWS_ACCOUNT_ID_ENV] = accountId

    # run s3cmd.
    cmd = "s3cmd.rb get \"" + bucket + ":" + key + "\" \"" + targetFile + "\""
    shell.sh(cmd)


  def untar(self, tarball, destDir=None, doUnzip=True):
    """ expands a tarball. tar xf[z] tarball [-C destdir] """

    cmd = "tar xf"

    if doUnzip:
      cmd = cmd + "z"

    cmd = cmd + " \"" + tarball + "\" "

    if destDir != None:
      parentDir = os.path.dirname(destDir)
      dirutils.mkdirRecursive(parentDir)
      cmd = cmd + " -C \"" + destDir + "\""

    shell.sh(cmd)



