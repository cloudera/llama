#!/bin/sh
# Copyright 2009 Cloudera, inc.
set -e

usage() {
  echo "
usage: $0 <options>
  Required not-so-options:
     --cloudera-source-dir=DIR   path to cloudera distribution files
     --build-dir=DIR             path to pig dist.dir
     --prefix=PREFIX             path to install into

  Optional options:
     --doc-dir=DIR               path to install docs into [/usr/share/doc/pig]
     --lib-dir=DIR               path to install pig home [/usr/lib/pig]
     --installed-lib-dir=DIR     path where lib-dir will end up on target system
     --bin-dir=DIR               path to install bins [/usr/bin]
     --examples-dir=DIR          path to install examples [doc-dir/examples]
     ... [ see source for more similar options ]
  "
  exit 1
}

OPTS=$(getopt \
  -n $0 \
  -o '' \
  -l 'cloudera-source-dir:' \
  -l 'prefix:' \
  -l 'doc-dir:' \
  -l 'lib-dir:' \
  -l 'installed-lib-dir:' \
  -l 'bin-dir:' \
  -l 'examples-dir:' \
  -l 'build-dir:' -- "$@")

if [ $? != 0 ] ; then
    usage
fi

eval set -- "$OPTS"
while true ; do
    case "$1" in
        --cloudera-source-dir)
        CLOUDERA_SOURCE_DIR=$2 ; shift 2
        ;;
        --prefix)
        PREFIX=$2 ; shift 2
        ;;
        --build-dir)
        BUILD_DIR=$2 ; shift 2
        ;;
        --doc-dir)
        DOC_DIR=$2 ; shift 2
        ;;
        --lib-dir)
        LIB_DIR=$2 ; shift 2
        ;;
        --installed-lib-dir)
        INSTALLED_LIB_DIR=$2 ; shift 2
        ;;
        --bin-dir)
        BIN_DIR=$2 ; shift 2
        ;;
        --examples-dir)
        EXAMPLES_DIR=$2 ; shift 2
        ;;
        --)
        shift ; break
        ;;
        *)
        echo "Unknown option: $1"
        usage
        exit 1
        ;;
    esac
done

for var in CLOUDERA_SOURCE_DIR PREFIX BUILD_DIR ; do
  if [ -z "$(eval "echo \$$var")" ]; then
    echo Missing param: $var
    usage
  fi
done

DOC_DIR=${DOC_DIR:-$PREFIX/usr/share/doc/pig}
LIB_DIR=${LIB_DIR:-$PREFIX/usr/lib/pig}
INSTALLED_LIB_DIR=${INSTALLED_LIB_DIR:-/usr/lib/pig}
EXAMPLES_DIR=${EXAMPLES_DIR:-$DOC_DIR/examples}
BIN_DIR=${BIN_DIR:-$PREFIX/usr/bin}
CONF_DIR=/etc/pig
CONF_DIST_DIR=/etc/pig/conf.dist
HADOOP_CONFIG_PATH=/etc/default/hadoop

# First we'll move everything into lib
install -d -m 0755 $LIB_DIR
(cd $BUILD_DIR && tar -cf - .) | (cd $LIB_DIR && tar -xf -)

# Remove directories that are going elsewhere
for dir in bin src lib-src conf docs tutorial test build.xml
do
   rm -rf $LIB_DIR/$dir
done

# Copy in the configuration files
install -d -m 0755 $PREFIX$CONF_DIST_DIR
cp $CLOUDERA_SOURCE_DIR/pig.properties $PREFIX$CONF_DIST_DIR
cp $CLOUDERA_SOURCE_DIR/pig-log4j.properties $PREFIX$CONF_DIR_DIST/log4j.properties

# Copy in /usr/bin/pig
install -d -m 0755 $BIN_DIR
cp $BUILD_DIR/bin/pig $BIN_DIR/pig
pig_file=$BIN_DIR/pig
# Modify pig to work with RPM (man hier)
sed -i -e "s|^.*export PIG_HOME.*\$|. $HADOOP_CONFIG_PATH\nexport PIG_HOME=\"/usr/lib/pig\"\nexport PIG_CONF_DIR=\"/etc/pig/\"\nexport PIG_LOG_DIR=\"/var/log/pig\"\n|" $pig_file
sed -i -e 's|^PIG_HADOOP_VERSION.*$|CLASSPATH="${CLASSPATH}:/usr/lib/hadoop/hadoop-*core.jar:/etc/hadoop/conf"|' $pig_file

# Copy in the docs
install -d -m 0755 $DOC_DIR
(cd $BUILD_DIR/docs && tar -cf - .)|(cd $DOC_DIR && tar -xf -)

install -d -m 0755 $EXAMPLES_DIR
PIG_JAR=$(basename $(ls $LIB_DIR/pig*core.jar))
sed -i -e "s|../pig.jar|/usr/lib/pig/$PIG_JAR|" $BUILD_DIR/tutorial/build.xml
(cd $BUILD_DIR/tutorial && tar -cf - .)|(cd $EXAMPLES_DIR && tar -xf -)

# Pig log directory
install -d -m 1777 $PREFIX/var/log/pig
