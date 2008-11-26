# (c) Copyright 2008 Cloudera, Inc.
#
# module: com.cloudera.distribution.java
#
# Detects current Java installation version and determines if it
# meets our criteria

import os
import sys

from   com.cloudera.distribution.constants import *
import com.cloudera.tools.shell as shell
import com.cloudera.util.output as output

# TODO: Test this with various things pointing at Jikes, Harmony, GCJ, etc...
# It is known to correctly match Sun JDK 1.6

def canFindJDK(maybeJavaHome, properties):
  """ Determines if the JDK is installed at $maybeJavaHome with an appropriate
      version. the only version we support is sun java 1.6 """

  output.printlnDebug("Looking for JDK 1.6")
  if maybeJavaHome == None or len(maybeJavaHome) == 0:
    output.printlnDebug("No java home set")
    return False

  output.printlnDebug("Looking for executables")

  # check that $JAVA_HOME/bin/java and javac exist
  javaBin = os.path.join(maybeJavaHome, "bin/java")
  javacBin = os.path.join(maybeJavaHome, "bin/javac")

  if not os.path.exists(javaBin):
    output.printlnError("Could not find java binary")
    return False

  if not os.path.exists(javacBin):
    output.printlnError("Could not find javac binary")
    return False

  output.printlnDebug("Found java and javac in " + maybeJavaHome)

  # check that they have the proper version number.

  try:
    javacLines = shell.shLines(javacBin + " -version")
    if len(javacLines) > 0:
      javacVerStr = javacLines[0].strip()
      output.printlnDebug("Got javac version: " + javacVerStr)
      if not javacVerStr.startswith("javac 1.6."):
        # incorrect javac version
        output.printlnError("javac reports incompatible version: " \
            + javacVerStr)
        return False
  except shell.CommandError:
    output.printlnError("Could not get javac version")
    return False

  try:
    javaLines = shell.shLines(javaBin + " -version")
    if len(javaLines) > 1:
      javaVerStr = javaLines[0].strip()
      output.printlnDebug("Got java version: " + javaVerStr)
      if not javaVerStr.startswith("java version \"1.6."):
        # incorrect javac version
        output.printlnError("java reports incompatible version: " \
            + javaVerStr)
        return False
      runtimeStr = javaLines[1].strip()
      output.printlnDebug("Got runtime identifier: " + runtimeStr)
      if not runtimeStr.startswith("Java(TM) SE Runtime Environment") \
          and not runtimeStr.startswith("Java(TM) EE Runtime Environment"):
        output.printlnVerbose("Got invalid runtime string: " + runtimeStr)
        output.printlnError( \
           """This does not appear to be an installation of Sun Java 1.6.
Hadoop requires that JAVA_HOME identify Sun Java.""")
        return False

  except shell.CommandError:
    output.printlnError("Could not get java version")
    return False

  # passes all our checks!
  output.printlnDebug("Java appears to be installed correctly.")
  return True



def getJavaHome(properties):
  """ Return the JAVA_HOME that we should set in Hadoop, etc. """

  javaHomeProp = properties.getProperty(JAVA_HOME_KEY)
  if javaHomeProp != None:
    # The user specified $JAVA_HOME or --java-home
    return javaHomeProp

  return None

def guessJavaHome(properties):
  """ If JAVA_HOME is not specified, can we infer it from `which java`? """

  try:
    lines = shell.shLines("which java")
    if len(lines) > 0:
      javaLine = lines[0].strip()
      # This might be /usr/bin/java
      # We should resolve this all the way through symlinks..
      # it'll then be in $JAVA_HOME/jre/bin/java
      # or $JAVA_HOME/bin/java
      realPath = os.path.realpath(javaLine)
      realDir = os.path.dirname(realPath)
      if realDir.endswith("jre/bin"):
        return os.path.abspath(os.path.join(realDir, "../.."))
      elif realDir.endswith("bin"):
        return os.path.abspath(os.path.join(realDir, ".."))
  except shell.CommandError:
    # clearly not.
    return None

  # 'which java' didn't seem to work.
  return None

