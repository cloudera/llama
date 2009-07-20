////
The format of this document is asciidoc.  
To learn more about format, visit http://www.methods.co.nz/asciidoc/.
You can also just skip to the Asciidoc Cheatsheat at http://powerman.name/doc/asciidoc.

To generate HTML from this page 
$ asciidoc README.txt
will generate README.html

To generate PDF from this page
$ asciidoc -b docbook README.txt && docbook2pdf README.xml
will generate README.pdf
////
Cloudera's Distribution for Hadoop (CDH)
========================================

This document explains how to build Cloudera's Distribution for Hadoop.  It
also covers setting up your build environments, the versioning and the release process, etc.

== Setting up your build environment

=== Python

You need to have version 2.5.x of Python installed.  Python 2.4 or 2.6 will
not work.

[NOTE]
You'll see that in many places `/usr/bin/python2.5` is hard-coded in 
script shabangs.  The reason for this is that the CentOS 5 python 2.4 package
installs `/usr/bin/python` instead of using a symlink.

=== Crepo and Stitch

*Crepo* is a git repository management tool.  *Stitch* is a higher-order
target definition and build system.  You'll need to have both tools installed
to build CDH.

[TIP]
Read the top-level 'README' files for both *Crepo* and *Stitch*

.Cloning the stitch and crepo repositories
----
$ git clone git://github.com/cloudera/stitch.git
$ git clone git://github.com/cloudera/crepo.git
----

'crepo' is run directly out of its git repository and only requires that you
have the 'simplejson' python module installed.

'stitch' requires installation which is documented in the top-level 'INSTALL'
file in the 'stitch' git repository (mostly a `python setup.py install`).

=== 64-bit machine/VM

You need to run the build process on a 64-bit machine.  You can just install
the 64-bit version of CentOS or Ubuntu (for example) inside of the VM.  This
is required for us to build both 32 and 64 bit native libraries.

=== Java and Apache Forrest

The build process requires that you have Apache Forrest and Sun
Java JDK 5 and 6 (for JDK 6 you need both the 32-bit and 64-bit JDK).
The build script need to know the locations of these tools.

In the root directory of the 'cdh' git repository, you could create a file
called 'my.properties' which points to the location of these tools, e.g.

.Sample contents of my.properties file
----
java32.home=/home/matt/bin/jdk1.6.0_14_i586
java64.home=/home/matt/bin/jdk1.6.0_14_x86_64
java5-home=/home/matt/bin/jdk1.5.0_19
forrest-home=/home/matt/bin/apache-forrest-0.8
----

=== Apache Ant

You need to install Apache version greater than or equal to 1.7.1.

[IMPORTANT]
You should download the Apache Ant tarball from http://ant.apache.org/.  There
are currently no good Debian and RPM packages for Apache Ant. 

.Setting Ant Environment Variables
----
export ANT_HOME="/workspace/apache-ant-1.7.1"
export PATH="${PATH}:${ANT_HOME}/bin"
----

include::README-hadoop.txt[]

== Package Versioning

It's important for our customers and community to understand exactly what our
packages contain.  Among other things it makes it easier for us to debug their problems 
and helps them plan upgrades as we move forward with features and fixes.

=== Format

.Examples of Hadoop packages 
[grid="all"]
`-----------------------`---------------`--------`-------------------
Name                    Version          Release  Suffix
---------------------------------------------------------------------
hadoop-0.18             0.18.3+68        1        cloudera.noarch.rpm
hadoop-0.18             0.18.3+68+rl.2   1        cloudera.noarch.rpm
hadoop-0.18-namenode    0.18.3+68        1        cloudera.noarch.rpm
hadoop-0.20             0.20.0+23.5      1        cloudera.noarch.rpm
hadoop-0.20-conf-pseudo 0.20.0+23.5      1        cloudera.noarch.rpm 
---------------------------------------------------------------------

with the full name of these package being, e.g.
----
hadoop-0.18-0.18.3+68-1.cloudera.noarch.rpm
hadoop-0.18-0.18.3+68+rl.2-1.cloudera.noarch.rpm
hadoop-0.18-namenode-0.18.3+68-1.cloudera.noarch.rpm
hadoop-0.20-0.20.0+23-1.cloudera.noarch.rpm
hadoop-0.20-conf-pseudo-0.20.0+23-1.cloudera.noarch.rpm
----

==== Name

The name of the package include the base version of Hadoop is created
against.  This allow people to install two versions of Hadoop at the same
time very similarly to installing `gcc-3` and `gcc-4` at the same time.  The
packages are built in a way to ensure there are no file conflicts, e.g.
configuration files are in '/etc/hadoop-0.18' and '/etc/hadoop-0.20'.

==== Version

The version of the packages explicitly states the version of Hadoop which is
is built against, e.g. '0.18.3', and the number of patches applied, e.g.
'+68'.  The patch-level can also have a dot release, e.g. '+23.5'.  These
patch numbers are automatically managed by the build process scripts which use
`git` branching.  This guarantees that each package released can be traced to
the exact git hash in our repositories.

[NOTE]
Both Debian and RedHat package managers have the concept of version
_segments_.  The package managers parse the version by reading as many
+a-zA-Z0-9+ characters as possible to create a segment.  People commonly use a
dot ('.') as the segment delimiter but you can also use other characters, e.g.
plus ('\+').  So for example, +0.18.3+68+ has four segments: 0, 18, 3, and 68.

If we have customers that need customizations that are not more generally
useful, we can easily create a customer-specific branch for releases, e.g.
'hadoop-0.18-0.18.3+68+rl.2-1.cloudera.noarch.rpm'.  This package will be
interpretted by package managers as an upgrade of '0.18-0.18.3+68' _since it
has extra segments_: 'rl' and '2'.  The segment 'rl' is the customer tag and
the '2' is the number of customer-specific patches applied for the release.

[NOTE]
If you glance at the repository list at http://git.sf.cloudera.com/, you'll see
that there are some repositories that are paired with their Apache
counterpart, e.g. 'apache-hadoop'/'hadoop', 'apache-pig','pig', etc.  The
repositories with the 'apache-' prefix are read-only internal mirrors of the
Apache git repository.  All cloudera work on hadoop is in the read/write
repository, e.g. 'hadoop'.  This allows us to use `git` branches and separate
remotes to automatically manage our patch process.

==== Release

The release number should rarely change (if ever).  In the case that we need to make a
packaging change _without changing the package contents_, we will increment
the release number.  

==== Suffix

This contains information about the architecture, platform and package is
released for.  

== Release Process

A release is comprised of a *number* of packages (e.g. 'hadoop-0.18', 'hadoop-pig',
'hadoop-hive', 'hadoop-0.20-conf-pseudo', etc), which have been built to work
together.

[NOTE]
When we started building CDH, our emphasis was on easy of installation and
stability.  We learned, however, that some customers are happy to sacrifice
some stability for new features.  To allow customers to subscribe to differing
levels of stability and functionality, we are publishing CDH into three
different repositories: 'stable', 'testing' and 'unstable'.

.Repository Definitions
stable::
Production-ready packages.  No changes will break interfaces or
running code in production.  Only patches for serious bugs or security issues
will be accepted.
testing::
Balance of stability and features.  Not as well-tested as our stable
repository but shouldn't have glaring issues.  Changes which break interfaces
can occur but are avoided if possible.
unstable:: Automatic nightly builds 

=== Life of a Package

All packages in a release follow the same life cycle.

