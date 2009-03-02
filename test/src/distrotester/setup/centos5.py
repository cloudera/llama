# (c) Copyright 2009 Cloudera, Inc.
#
# module: distrotester.setup.fedora8
#
# Sets up expected/necessary packages on a CentOS 5 installation

import logging
import os

import com.cloudera.tools.shell as shell

from   distrotester.constants import *
from   distrotester.setup.platformsetup import PlatformSetup
import distrotester.setup.redhatcommon as redhatcommon


class Centos5Setup(redhatcommon.RedHatCommonSetup):
  """ Performs setup of packages on a CentOS 5.2 installation.
      Works on both i386 and x86_64 architectures; one of the
      strings "i386" or "x86_64" must be provided as the 'arch'
      parameter to the c'tor.
  """

  def __init__(self, arch, properties):
    redhatcommon.RedHatCommonSetup.__init__(self, arch, properties)


  def remoteBootstrap(self):
    """ Remote bootstrap for CentOS 5 """

    # Need to make sure we can use python!
    # Also, ruby for s3cmd
    return [
      "yum -y install python python-devel ruby rsync"
    ]


  def setup(self):
    """ Install on CentOS 5.2. """

    # perform common setup steps we share with other RH-based distributions
    self.redhat_common_setup()

    # install python 2.5 to run the rest of our code.
    logging.debug("Installing python 2.5")
    python_25_package = "python25-2.5.1-25.i386.rpm"
    python_package_dest = os.path.join(PACKAGE_TARGET, python_25_package)
    self.s3get(self.bucket, "packages/" + python_25_package, python_package_dest)
    shell.sh("yum -y install \"" + python_package_dest + "\"")

    # install other packages that make this a more sane system to use.
    logging.debug("Installing some more packages with yum...")
    shell.sh("yum -y install screen lzo")
    shell.sh("yum -y install xml-commons-apis")

    # if this is x86_64, then we need to install the correct version
    # of curl for compatibility with php (used by the support portal).
    if self.arch == "x86_64":
      logging.debug("Installing correct x86_64 curl package")
      shell.sh("yum -y remove curl.i386")
      shell.sh("yum -y install curl.x86_64")



