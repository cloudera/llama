# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.installerror
#
# Defines the InstallError class representing exceptions
# that occur when running the installation process

class InstallError(Exception):
  """ Errors when running a ToolInstall """

  def __init__(self, value):
    self.value = value

  def __str__(self):
    return str(self.value)

