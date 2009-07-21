== Building CDH Hadoop

The CDH build process is comprised of several targets.

=== The ':supportedHadoopPackage' target

The main target is ':supportedHadoopPackage', defined in '//targets'. This target
builds the supported Hadoop tarball.

[NOTE]
Since we want the tarball to be useful for people running both 32-bit and
64-bit operating systems, we need to build native libraries for both
architectures. Therefore, you need to be running 64-bit Linux and have both
64-bit and 32-bit JDKs.  You can download them from Sun and unpack
them anywhere you like. Then create a file 'my.properties' in the git root
which sets "java32.home" and "java64.home" to point to those directories.

.Preparing for a build
----
$ git clone git@git.sf.cloudera.com:cdh.git
$ cd cdh
$ git checkout -b <release> origin/<release>
$ crepo.py init
----
The above commands clone the 'cdh' repos, checkout the <release> branch of 'cdh' to build and
initializing the directory structure based on the 'manifest.json' file in the
top-level of the 'cdh' repository using `crepo.py`.  *Crepo* will put all
cloned repositories in the 'repos' subdirectory.

.Checking the status crepo-managed build structure 
----
$ crepo.py fetch
$ crepo.py status
----
will tell you if your repositories are dirty or out of date for example.

Once you have the directory structure created, you can use stitch to build the
release.  

////
TODO: This is how to build hadoop and NOT a release.  Fix this.
////

.Building the Hadoop tarball
----
$ stitch
$ ./sbuild :supportedHadoopPackage
----

.Finding the :supportedHadoopPackage output
----
$ ls build/redist/_supportedHadoopPackage/hadoop-build/build/*tar.gz
----

How it works

I'll structure this as an outline since there is a lot of nested scripting
going on:

.':supportedHadoopPackage' target calls:
.. 'stitch-ext/cloudera.py:SetupBuildStep' target which:
... calls '//tools/setup-package-build' script which:
.... unpacks the pristine source into the assembly directory
.... calls the '//tools/create-cloudera-dir' script which:
..... formats the git patch series into 'cloudera/patches/'
..... writes the git log into 'cloudera/CHANGES.cloudera.txt'
..... copies 'tools/redist' into 'cloudera/'
..... calls '//tools/generate-build-properties' script which creates 'cloudera/build.properties', 
which includes the git hash, the base branch, the branch being built, the version number, etc.
... calls 'cloudera/apply-patches' script which just calls 'git-apply' on everything inside 'cloudera/patches'
... calls 'cloudera/do-release-build' with +JAVA32_HOME+ and +JAVA64_HOME+ set
by properties in 'build.properties' or 'my.properties'
.... checks JDKs that they are the right architecture
.... runs the right ant commands to build a Hadoop release (pretty much
following the HowToRelease hadoop wiki)

=== Source Package Targets

Brief summary: the package targets take the output of '//:supportedHadoopPackage'
and package them as source RPMs and source Debs. There are currently no stitch
targets to actually build the binary RPMs and debs from the sources. For some
example of how to do that, look at 'ec2_build/build_{rpm,deb}.sh'

The src rpm/deb targets are 'repos/hadoop-package/:deb' and 'repos/hadoop-package/:rpm'

.Turning source RPM into binary RPMs
----
$ rpmbuild --rebuild build/rpm/SRPMS/hadoop-*.src.rpm
$ rpmbuild --rebuild --target noarch build/rpm/SRPMS/hadoop-*.src.rpm
----

//// 
TODO: Debian example?
////
