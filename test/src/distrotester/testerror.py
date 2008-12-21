# (c) Copyright 2008 Cloudera, Inc.
#
# module: distrotester.testerror
#
# Contains TestError class representing an error during testing

class TestError(Exception):
  """ Errors when running a test process """

  def __init__(self, value):
    self.value = value

  def __str__(self):
    return str(self.value)

