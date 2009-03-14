#
# Distribution RPM spec file
#
# (c) Copyright 2009 Cloudera, Inc.
#
# This contains the virtual package that represents the entire distribution
#

%define distro_name cloudera-distribution
%define distro_version %(echo @VERSION@ | awk -F- '{print $1}')

Name: %{distro_name}
Vendor: Cloudera <http://www.cloudera.com/>
Version: %{distro_version}
Release: @RELEASE@
Summary: The Cloudera Distribution for Hadoop contains Apache Hadoop and several bundled utilities such as Hive, Pig, and other helpful tools for large-scale data processing.
License: Apache License v2.0
URL: http://www.cloudera.com
Group: Development/Libraries
Buildroot: @RPMBUILDROOT@
Requires: hadoop, hadoop-hive, hadoop-pig, mrunit

%description
The Cloudera Distribution for Hadoop contains Apache Hadoop and several
bundled utilities and helpful tools for large-scale data processing.

Hadoop is a software platform that lets one easily write and run
applications that process vast amounts of data.

The Cloudera Distribution for Hadoop contains Apache Hadoop as well
as several related tools:

* Hive - a business intelligence tool that allows manipulation of
  datasets using a SQL-like language.

* Pig - a dataflow language that operates on top of MapReduce.

* Scribe - a log aggregation framework to facilitate system debugging.

* MRUnit - a unit test framework for MapReduce programs.

%install
%__rm -rf $RPM_BUILD_ROOT
%__install -d -m 0755 $RPM_BUILD_ROOT/etc/
echo %{distro_version} > $RPM_BUILD_ROOT/etc/cloudera-version

%files
%defattr(-,root,root)
%attr(0755,root,root)/etc/cloudera-version

