#!/bin/sh
# (c) Copyright 2009, Cloudera, inc.
#
# Script to install necessary packages for managing the apt repo
# in a Debian Lenny EC2 instance
#
# This is supposed to be called from remote_machine_setup.sh, not by hand!


# Install some package dependencies

grep -q 'GNUPGHOME' .bashrc || \
echo '
export GNUPGHOME=$HOME/gpg-home
export GPG_TTY=`tty`

eval `gpg-agent --daemon`
' >> .bashrc