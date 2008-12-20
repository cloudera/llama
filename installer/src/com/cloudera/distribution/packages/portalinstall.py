# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.portalinstall
#
# Defines the ToolInstall instance that installs the web portal

from com.cloudera.distribution.installerror import InstallError
from com.cloudera.distribution.toolinstall import ToolInstall
from com.cloudera.distribution.constants import *

import com.cloudera.distribution.arch as arch
import com.cloudera.util.output as output
import com.cloudera.tools.shell as shell

class PortalInstall(ToolInstall):

  def __init__(self, properties):
    ToolInstall.__init__(self, "Portal", properties)

    # need LogMover because we need MySQL
    self.addDependency("LogMover")

    # need Hadoop because the portal source code
    # is part of the Hadoop package
    self.addDependency("Hadoop")

  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """

    htdocs = self.getPortalDest()
    files = os.listdir(htdocs)
    if len(files) != 0:
      output.printlnInfo("""
Your htdocs (%(htdocs)s) folder has files in it.  This installer will add
files to this directory and possibly overwrite data without backup.  Please
either move your files elsewhere or erase them completely if they are
the default set of documents for Apache, Lighttpd, etc.
""" % {'htdocs': htdocs,
       })


      raise InstallError(htdocs + " has files; please move or erase them")

  def install(self):
    """ Run the installation itself. """

    # the portal is only installed on the NN
    if self.isMaster():
      self.install_httpd()
      self.install_portal()

  def install_httpd(self):
    """
    Installs Lighttpd with PHP5 and MySQL support. 
    Assumes MySQL is already installed
    """

    # instructions for this were taken from:
    #  FC: http://www.howtoforge.com/lighttpd_php5_mysql_fedora7
    #  Ubuntu: http://www.ubuntugeek.com/lighttpd-webserver-setup
    #                -with-php5-and-mysql-support.html

    # install lighttpd
    pckg = {arch.PACKAGE_MGR_DEBIAN: "lighttpd",
            arch.PACKAGE_MGR_RPM: "lighttpd",
            }
    self.installPackage(pckg)

    # install necessary PHP and MySQL modules
    arch_inst = arch.getArchDetector()
    if arch_inst.getPackageMgr() == arch.PACKAGE_MGR_DEBIAN:
      pckg = {arch.PACKAGE_MGR_DEBIAN: "php5-cgi",
              }
      self.installPackage(pckg)

      pckg = {arch.PACKAGE_MGR_DEBIAN: "php5-mysql",
              }
      self.installPackage(pckg)
    elif arch_inst.getPackageMgr() == arch.PACKAGE_MGR_RPM:
      pckg = {arch.PACKAGE_MGR_RPM: "lighttpd-fastcgi",
              }
      self.installPackage(pckg)

      pckg = {arch.PACKAGE_MGR_RPM: "php-cli",
              }
      self.installPackage(pckg)

      pckg = {arch.PACKAGE_MGR_RPM: "php-mysql",
              }
      self.installPackage(pckg)
    else:
      raise InstallError("Could not determine your package manager")

    # update php and lighttpd config files
    good_http_conf = ""
    good_php_ini = ""
    php_ini_dest = ""
    platform = arch_inst.getPlatform()
    if platform == arch.PLATFORM_UBUNTU:
      good_http_conf = os.path.join(DEPS_PATH,
                                    "ubuntu-8.04-i386_lighttpd.conf")
      good_php_ini = os.path.join(DEPS_PATH,
                                  "ubuntu-8.04-i386_php.ini")
      php_ini_dest = "/etc/php5/cgi/php.ini"
    elif platform == arch.PLATFORM_FEDORA:
      good_http_conf = os.path.join(DEPS_PATH,
                                    "fedora8-i386_lighttpd.conf")
      good_php_ini = os.path.join(DEPS_PATH,
                                  "fedora8-i386_php.ini")
      php_ini_dest = "/etc/php.ini"

    try:
      shell.shLines("cp " + good_http_conf + " /etc/lighttpd/lighttpd.conf")
    except shell.CommandError:
      raise InstallError("Could not copy a custom lighttpd configuration")

    try:
      shell.shLines("cp " + good_php_ini + " " + php_ini_dest)
    except shell.CommandError:
      raise InstallError("Could not copy a custom php.ini")

    try:
      shell.shLines("/etc/init.d/lighttpd restart")
    except shell.CommandError:
      raise InstallError("Could not restart lighttpd using /etc/init.d/lighttpd")

    output.printlnInfo("Installed lighttpd with PHP and MySQL support.")

  def install_portal(self):
    """
    Install the portal by copying to lighttpd's docroot.
    Because LogMover has already been run, the DB has
    already been bootstrapped
    """

    # get the location of the hadoop distribution
    # so we can copy the portal files from there
    hadoop_folder = self.getHadoopLocation()
    src_folder = os.path.join(hadoop_folder,
                              PORTAL_SRC_LOCATION)
    src_folder = os.path.join(src_folder,
                              "*")

    dest_folder = self.getPortalDest()
    try:
      cpLines = shell.shLines("cp -R " + src_folder + " " + dest_folder)
    except shell.CommandError:
      raise InstallError("Portal web app could not be copied to " + dest_folder)

    output.printlnInfo("Successfully installed the portal")

    # sed -i -e 's/localhost/new.domain/' index.html
    # TODO

    self.updatePortalConf()

  def getHadoopLocation(self):
    """Gets the location where Hadoop is installed"""
    return os.path.join(self.getInstallBasePath(),
                        HADOOP_INSTALL_SUBDIR)

  def getPortalDest(self):
    """Gets the dest where portal will be installed"""
    arch_inst = arch.getArchDetector()
    if arch_inst.getPlatform() == arch.PLATFORM_FEDORA:
      dest_folder = LIGHTTPD_FC_HTDOCS
    elif arch_inst.getPlatform() == arch.PLATFORM_UBUNTU:
      dest_folder = LIGHTTPD_UBUNTU_HTDOCS
    else:
      raise InstallError("Your platform is not currently supported.")

    return dest_folder

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    pass

  def updatePortalConf(self):
    """
    Update the portal by telling it where the hadoop-site.xml
    file is
    """

    hadoop_folder = self.getHadoopLocation()
    hadoop_site = os.path.join(hadoop_folder, "conf/hadoop-site.xml")

    portal_dest = self.getPortalDest()
    portal_conf = os.path.join(portal_dest, "hadoop-site-location")

    try:
      echoLines = shell.shLines("echo '" + hadoop_site + "' > " + portal_conf)
    except:
      raise InstallError("Portal web app could not be configured")

    output.printlnInfo("Portal configuration updated")

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    # TODO postinstall Portal
    pass

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify Portal

  def getRedeployArgs(self):
    """ Provide any command-line arguments to the installer on the slaves """
    # TODO: Return anything necessary.
    return []

