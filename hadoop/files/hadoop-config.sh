# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# included in all the hadoop scripts with source command
# should not be executable directly
# also should not be passed any arguments, since we need original $*

# Honor the JAVA_HOME variable if set, otherwise try to find Java
if [ -z $JAVA_HOME ]; then
  if [ -e @JAVA_HOME@ ]; then
	export JAVA_HOME="@JAVA_HOME@"
  else
	cat <<MSG
+======================================================================+
|      Error: JAVA_HOME is not set and Java could not be found         |
+----------------------------------------------------------------------+
| Please download the latest Sun JDK from the Sun Java web site        |
|       > http://java.sun.com/javase/downloads/ <                      |
|                                                                      |
| NOTE: This script will find Sun Java whether you install using the   |
|       binary or the RPM based installer.                             |
+======================================================================+
MSG
	exit 1
  fi
fi

# the root of the Hadoop installation
export HADOOP_HOME="${HADOOP_HOME:-/usr/lib/hadoop}"

#check to see if the conf dir is given as an optional argument
if [ $# -gt 1 ]
then
    if [ "--config" = "$1" ]
	  then
	      shift
	      confdir=$1
	      shift
	      HADOOP_CONF_DIR=$confdir
    fi
fi
 
# Allow alternate conf dir location.
HADOOP_CONF_DIR="${HADOOP_CONF_DIR:-/etc/hadoop/conf}"
HADOOP_LOG_DIR="${HADOOP_LOG_DIR:-/var/log/hadoop}"

#check to see it is specified whether to use the slaves or the
# masters file
if [ $# -gt 1 ]
then
    if [ "--hosts" = "$1" ]
    then
        shift
        slavesfile=$1
        shift
        export HADOOP_SLAVES="${HADOOP_CONF_DIR}/$slavesfile"
    fi
fi
