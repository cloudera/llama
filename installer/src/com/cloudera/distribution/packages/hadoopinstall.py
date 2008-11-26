# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.packages.hadoopinstall
#
# Defines the ToolInstall instance that installs Hadoop

from   com.cloudera.distribution.installerror import InstallError
import com.cloudera.distribution.java as java
from   com.cloudera.distribution.toolinstall import ToolInstall
import com.cloudera.util.output as output
import com.cloudera.util.prompt as prompt

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
    ToolInstall.__init__(self, "Hadoop", properties)
    self.addDependency("GlobalPrereq")


  def precheck(self):
    """ If anything must be verified before we even get going, check those
        constraints in this method """

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
    while not java.canFindJDK(javaHome, self.properties):
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
    # TODO: Get master address
    # make sure that this is an FQDN, not an IP address
    # Put that in the 'master' file
    # TODO: Slaves file
    # TODO: Parse slaves file for line count to recommend hadoop-site vals
    # TODO: Fill out necessary elements of hadoop-site.xml
    pass

  def postInstall(self):
    """ Run any post-installation activities. This occurs after
        all ToolInstall objects have run their install() operations. """
    # TODO postinstall hadoop
    # TODO: Format DFS if the user wants it done
    pass

  def verify(self):
    """ Run post-installation verification tests, if configured """
    # TODO: Verify hadoop
    # TODO: Start Hadoop daemons if the user wants it done
    # TODO: Run  'bin/hadoop fs -ls /' to make sure it works
    # (do a touchz, ls, rm)
    # TODO: Run a sample 'pi' job.


