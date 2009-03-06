# (c) Copyright 2009 Cloudera, Inc.
#
# module: distrotester.setup.fedora8
#
# Sets up expected/necessary packages on a Fedora Core 8 installation


import os

import com.cloudera.tools.shell as shell

from   distrotester.constants import *
from   distrotester.setup.platformsetup import PlatformSetup
import distrotester.setup.redhatcommon as redhatcommon


class Fedora8Setup(redhatcommon.RedHatCommonSetup):
  """ Performs setup of packages on a Fedora Core 8 installation.
      Works on both i386 and x86_64 architectures; one of the
      strings "i386" or "x86_64" must be provided as the 'arch'
      parameter to the c'tor.
  """

  def __init__(self, arch, properties):
    RedHatCommonSetup.__init__(self, arch, properties)


  def remoteBootstrap(self):
    """ Remote bootstrap for Fedora 8 """

    # Need to make sure we can use python!
    # Also, ruby for s3cmd
    return [
      "yum -y install python python-devel ruby rsync"
    ]


  def setup(self):
    """ Install on Fedora 8. """

    # Perform common setup commands which are part of FC and other RH-based distros.
    self.redhat_common_setup()

    # install other packages that make this a more sane system to use.
    shell.sh("yum -y install screen lzo")
    shell.sh("yum -y install xeyes xauth")
    shell.sh("yum -y install xml-commons-apis")

    # if this is x86_64, then we need to install the correct version
    # of curl for compatibility with php (used by the support portal)
    if self.arch == "x86_64":
      shell.sh("yum -y remove curl.i386")
      shell.sh("yum -y install curl.x86_64")



