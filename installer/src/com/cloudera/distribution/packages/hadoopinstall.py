# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.hadoopinstall
#
# Defines the ToolInstall instance that installs Hadoop

from   com.cloudera.distribution.installerror import InstallError
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.util.output as output

def getJavaHomeFromUser(default):
  """ prompt the user for a valid value for JAVA_HOME """

  success = False
  while not success:
    javaHome = prompt.getString( \
        "Input the value for JAVA_HOME for Sun JRE 1.6", \
        default, False)
    if javaHome == None:
      output.printlnError("Error: Installing Hadoop requires " \
          + "a copy of Sun Java 1.6")
    else:
      success = True

  return javaHome


class HadoopInstall(ToolInstall):
  def __init__(self, properties):
    # TODO: Construct this with properties
    ToolInstall.__init__(self, "Hadoop", properties)


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """

    # TODO: Test this method

    # We have to check for Sun Java 1.6
    javaHome = java.getJavaHome(self.properties)
    if self.isUnattended():
      if javaHome == None:
        output.printlnError( \
           """JAVA_HOME is not set, and the Java installation path was not set
  with --java-home. Please restart the installer with this configured.""")
        raise InstallError("Could not find compatible JAVA_HOME")
      else:
        output.printlnVerbose("Using JAVA_HOME of " + javaHome)
    else:
      # confirm that our value for JAVA_HOME is correct.
      # If the user didn't specify one, try to look for a reasonable value
      if javaHome == None:
        javaHomeGuess = java.guessJavaHome(self.properties)
      else:
        javaHomeGuess = javaHome

      javaHome = getJavaHomeFromUser(javaHomeGuess)

    # now that we have a value for JAVA_HOME, assert that we can find
    # Java there.
    while not java.canFindJDK(javaHome, properties):
      output.printlnError("An invalid JAVA_HOME was specified; " \
          + "this must point to Sun Java 1.6")

      if self.isUnattended():
        # Nothing to do but give up
        raise InstallError("Could not find compatible JAVA_HOME")
      else:
        # Ask the user for a better value for JAVA_HOME.
        javaHome = getJavaHomeFromUser(javaHome)


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


