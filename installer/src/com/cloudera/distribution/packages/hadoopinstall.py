# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.hadoopinstall
#
# Defines the ToolInstall instance that installs Hadoop

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.util.output as output


class HadoopInstall(ToolInstall):
  def __init__(self):
    ToolInstall.__init__(self, "Hadoop")


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """

    # TODO: Test this method

    # We have to check for Sun Java 1.6
    # TODO: Where do we get properties from?
    javaHome = java.getJavaHome(properties)

    if javaHome == None:
      # TODO: If we are in an interactive setup, query user for JAVA_HOME
      output.printlnError( \
         """JAVA_HOME is not set, and the Java installation path was not set
with --java-home. Please restart the installer with this configured.""")
      raise InstallError("Could not find compatible JAVA_HOME")

    if not java.canFindJDK(javaHome, properties):
      output.printlnError("An invalid JAVA_HOME was specified; " \
          + "this must point to Sun Java 1.6")
      raise InstallError("Could not find compatible JAVA_HOME")
      # TODO: This should loop and allow the user to specify a different
      # Java_home


  def install(self):
    """ Run the installation itself. """
    pass
    # TODO install hadoop

  def configure(self):
    """ Run the configuration stage. This is responsible for
        setting up the config files and asking any questions
        of the user. The software is not installed yet """
    # TODO config hadoop
    pass

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    # TODO postinstall hadoop
    pass

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify hadoop


