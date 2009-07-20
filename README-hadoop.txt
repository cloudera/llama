== Building CDH Hadoop

The CDH build process is comprised of several targets.

The main target is ':supportedHadoopPackage', defined in '//targets'. This target
builds the supported Hadoop tarball.

=== The ':supportedHadoopPackage' target

Prerequisites:

To build a release tarball, you need:

  - 32-bit and 64-bit JVMs

[NOTE]
Since we want the tarball to be useful for people running both 32-bit and
64-bit operating systems, we need to build native libraries for both
architectures. Therefore, you need to be running 64-bit Linux and have both
64-bit and 32-bit JDKs.  You can download them from Sun and unpack
them anywhere you like. Then create a file 'my.properties' in the git root
which sets "java32.home" and "java64.home" to point to those directories.

To build:
----
$ stitch
$ ./sbuild :supportedHadoopPackage
----

# Build product ends up here:
----
$ ls build/redist/_supportedHadoopPackage/hadoop-build/build/*tar.gz
----

How it works:

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
..... calls '//tools/generate-build-properties' script which:
...... creates 'cloudera/build.propeties', which includes the git hash, the 
base branch, the branch being built, the version number, etc.
... calls 'cloudera/apply-patches' script which:
.... just calls 'git-apply' on everything inside 'cloudera/patches'
... calls 'cloudera/do-release-build' with +JAVA32_HOME+ and +JAVA64_HOME+ set
by properties in 'build.properties' or 'my.properties'
.... checks JDKs that they are the right architecture
.... runs the right ant commands to build a Hadoop release (pretty much
following the HowToRelease hadoop wiki)

== Package Targets

Brief summary: the package targets take the output of '//:supportedHadoopPackage'
and package them as source RPMs and source Debs. There are currently no stitch
targets to actually build the binary RPMs and debs from the sources. For some
example of how to do that, look at 'ec2_build/build_{rpm,deb}.sh'

The src rpm/deb targets are 'repos/hadoop-package/:deb' and 'repos/hadoop-package/:rpm'

For example, to build RPMs once the source RPM has been created, we run the
following
----
$ rpmbuild --rebuild build/rpm/SRPMS/hadoop-*.src.rpm
$ rpmbuild --rebuild --target noarch build/rpm/SRPMS/hadoop-*.src.rpm
----
to create the binary RPMs.  

//// 
TODO: Debian example?
////
