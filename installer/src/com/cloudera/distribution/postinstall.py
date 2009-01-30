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
# module: com.cloudera.distribution.postinstall

"""
    Keeps track of operations which must be performed after the software
    is "installed," but before it is usable. e.g., setting up directories
    in HDFS.

    This can be either directly executed (if the user allows starting of
    Hadoop services), or written out to a shell script to be run by the
    user later.
"""

import logging
import os
import tempfile

from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.toolinstall as toolinstall
import com.cloudera.tools.shell as shell


__install_actions = []


def add(action, fail_on_err=True, allow_repeat=False):
  """ Adds an action to the queue of actions to perform. If allow_repeat
      is False, search for exact text matches and do not add the action
      if it is a duplicate.

      If fail_on_err is true, then we require that this action succeed
      to continue running postinstall actions. An exit status > 0 implies
      that the install failed.
  """

  global __install_actions

  if not allow_repeat:
    for (cmd, critical) in __install_actions:
      if cmd == action:
        logging.debug("Skipping redundant postinstall action: " + action)
        return # action is already in the list; ignore.

  logging.debug("Adding postinstall action: " + action)
  __install_actions.append((action, fail_on_err))


def execute_actions():
  """ Run the enqueued actions. """

  global __install_actions

  for (action, critical) in __install_actions:
    try:
      shell.sh(action)
    except shell.CommandError:
      if critical:
        logging.error("Critical postinstall action failed: " + action)
        raise InstallError("Error execution postinstall action: " + action)
      else:
        logging.debug("Non-critical postinstall action failed: " + action)

  # We know that these actions started HDFS, so don't attempt to
  # start it again (or else the user will see ugly spurious warnings).
  hadoop_installer = toolinstall.getToolByName("Hadoop")
  if hadoop_installer != None:
    hadoop_installer.set_hdfs_started_flag()


def write_action_script():
  """
      Write a shell script to a file to execute all of the actions required.
      Returns the filename of the script created.
  """

  global __install_actions

  (oshandle, tmpfilename) = tempfile.mkstemp("", "postinstall-")
  logging.debug("Writing postinstall script to " + tmpfilename)
  try:
    handle = os.fdopen(oshandle, "w")
    # Write the script header
    handle.write("""#!/bin/sh
#
# Postinstall operation script for Cloudera Hadoop Distribution.
# This script runs operations necessary to continue the configuration
# of your Hadoop cluster before it is ready for use. This script should
# be run once by your administrator after the installation of the
# distribution is complete. Do not re-execute this script if you later
# add more nodes.
#
# This script is AUTOMATICALLY GENERATED. Do not modify this file.
#
bin=`dirname $0`
bin=`cd ${bin} && pwd`

function install_ops {
""")
    # actually write out the body of the script
    for (action, critical) in __install_actions:
      handle.write("  " + action + "\n")
      if critical:
        handle.write("  ret=$?\n")
        handle.write("  if [ \"$ret\" != \"0\" ]; then return \"$ret\"; fi\n")

    # write script epilogue
    handle.write("""
}

install_ops
ret=$?
if [ "$ret" != "0" ]; then
  echo "Errors were encountered during post-installation Hadoop setup steps."
  echo "This may be because the HDFS instance was not formatted."
  # TODO(aaron): Add example format command
else
  echo "Postinstall successful! You may remove this script"
  echo "($0)"
fi
exit $ret
""")
    handle.close()
  except OSError, ose:
    raise InstallError("OSError writing postinstall action script:" + str(ose))
  except IOError, ioe:
    raise InstallError("IOError writing postinstall action script:" + str(ioe))

  return tmpfilename

