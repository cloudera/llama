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

=== 64-bit machine/VM

You need to run the build process on a 64-bit machine.  You can just install
the 64-bit version of CentOS or Ubuntu (for example) inside of the VM.  This
is required for us to build both 32 and 64 bit native libraries.

=== Python

You need to have version 2.5.x of Python installed.  Python 2.4 or 2.6 will
not work.

[NOTE]
You'll see that in many places `/usr/bin/python2.5` is hard-coded in 
script shabangs.  The reason for this is that the CentOS 5 python 2.4 package
installs `/usr/bin/python` instead of using a symlink.

=== Git

*Git* is a popular SCM.  You'll need git to fetch various tools required for building.

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

=== Java and Apache Forrest

The build process requires that you have Apache Forrest and Sun
Java JDK 5 and 6 (for JDK 6 you need both the 32-bit and 64-bit JDK).
The build script need to know the locations of these tools.  It's easiest
to install each of these JDKs from the non-RPM and non-DEB .bin files.

[NOTE]
If you haven't yet checked out the 'cdh' git repository, do so with
*git clone ssh://git@git.sf.cloudera.com/cdh.git*

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

.Apt ant package 
----
$ apt-cache policy ant
ant:
  Installed: 1.7.1-0ubuntu1
  Candidate: 1.7.1-0ubuntu1
  Version table:
 *** 1.7.1-0ubuntu1 0
        500 http://us.archive.ubuntu.com intrepid/main Packages
        100 /var/lib/dpkg/status
----

[IMPORTANT]
For CentOS 5, you should download the Apache Ant tarball from http://ant.apache.org/ as
there are currently no good RPM packages for Apache Ant. 

.Setting Ant Environment Variables
----
export ANT_HOME="/workspace/apache-ant-1.7.1"
export PATH="${PATH}:${ANT_HOME}/bin"
----

include::README-hadoop.txt[]

=== gcc and other native components

You need 'zlib headers', 'gcc', 'gcc headers', 'gcc c++', 'gcc c++ headers', 'lzo headers', and 'glibc headers' to compile
the Hadoop native libraries. This can all be installed with your favorite package manager.

=== rpmbuild

You need rpmbuild for building RPM packages.  Use your favorite package manager to install rpmbuild.

== Package Versioning

It's important for our customers and community to understand exactly what our
packages contain.  Among other things it makes it easier for us to debug their problems 
and helps them plan upgrades as we move forward with features and fixes.

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

=== Name

The name of the package include the base version of Hadoop is created
against.  This allow people to install two versions of Hadoop at the same
time very similarly to installing `gcc-3` and `gcc-4` at the same time.  The
packages are built in a way to ensure there are no file conflicts, e.g.
configuration files are in '/etc/hadoop-0.18' and '/etc/hadoop-0.20'.

=== Version

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

=== Release

The release number should rarely change (if ever).  In the case that we need to make a
packaging change _without changing the package contents_, we will increment
the release number.  

=== Suffix

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

These repositories currently reside at http://archive.cloudera.com/releases/.

=== Life of a Package

All packages in a release follow the same life cycle.  This section will walk
through this process from beginning to end.

The version scheme we use (documented above) allows us to know exactly how
many patches have been applied to each component (e.g. hadoop, pig, hive, etc)
of our distribution.  The patch level is the number(s) directly following
the base version, e.g. 'package-0.1+45'.  The patch level would be '45' in this
example.  It's also possible that we have "dot" patch levels, e.g.
'package-0.1+45.5' or 'package-0.1+45.5.9'.  The patch levels would be '45.5'
and '45.5.9' respectively in this example.

==== Unstable repository

All packages begin their life in the 'unstable' repository as part of an
automated build process.  For example, a source change to hadoop's '0.20'
branch will cause a cascading build of 'hadoop-0.20', 
'hadoop-0.20-conf-pseudo', etc.

[IMPORTANT]
Each patch to a component causes the patch level to increase, e.g.
'package-0.1+10' to 'package-0.1+11' to 'package-0.1+12', etc.  A revert of a 
patch causes this number to *increase* as well.  Patch levels *never* decrease
and there will never be a "dot" patch level, e.g. '1.3', '6.23' in the
'unstable' repository. Each package just keeps marching forward.

==== Promoting 'unstable' package to 'testing'

Once an 'unstable' package has been found to show enough stability (through
automated testing, customer feedback, etc), it is promoted to the 'testing'
repository.  Promotion requires only that the package is copied from the
'unstable' apt/yum repository to the 'testing' apt/yum repository.

[NOTE] 
The package version never has any text assigning it to 'testing' or 'unstable', e.g. 
'package-0.10-testing', 'package-0.10-unstable'.  This isn't necessary and
increases the package maintainence costs.

IMPORTANT: put explicit requirements here for promotion

==== Testing repository

Once a package is in the 'testing' repository, it may have "dot" patch levels.

[NOTE]
Packages in the 'testing' repository are not *required* to have a "dot" patch
level.  It's possible (however unlikely) that an 'unstable' package proved to
be stable enough for promotion without any changes.

==== Promoting 'testing' to 'stable'

After a number of months of proven stability in production environments and
rigorous testing, a package in the 'testing' repository may be promoted to the
'stable' repository.

IMPORTANT: put explicit requirement here for promotion

==== Stable repository

Once a package is in the 'stable' repository, it may have "dot" patch levels.

[NOTE]
Packages in the 'stable' repository are not *required* to have "dot" patch
levels.  It's possible (however unlikely) that an 'unstable' or 'testing'
package proved to be stable enough for promotion without any changes.

=== Mapping releases to 'cdh' git repository

Explain how branching/tagging in 'cdh.git' relate to our 'CDH' releases.
