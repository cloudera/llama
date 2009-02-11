# 
# Hadoop RPM spec file 
# 
%define opt_hadoop /opt/hadoop-@VERSION@
%define doc_hadoop /usr/share/doc/hadoop-@VERSION@
%define hadoop_name hadoop
%define hadoop_build_path @PKGROOT@/build/hadoop-@VERSION@

Name: %{hadoop_name} 
Vendor: Hadoop
Version: @RPMVERSION@
Release: @RELEASE@
Summary: Hadoop is a software platform that lets one easily write and run applications that process vast amounts of data. 
License: Apache License v2.0
URL: http://hadoop.apache.org/core/
Group: System/Daemons
Buildroot: @RPMBUILDROOT@
Provides: %{hadoop_name}

%define global_description Hadoop is a software platform that lets one easily write and  \
run applications that process vast amounts of data. \
\
Here's what makes Hadoop especially useful: \
* Scalable: Hadoop can reliably store and process petabytes. \
* Economical: It distributes the data and processing across clusters  \
              of commonly available computers. These clusters can number  \
              into the thousands of nodes. \
* Efficient: By distributing the data, Hadoop can process it in parallel  \
             on the nodes where the data is located. This makes it  \
             extremely rapid. \
* Reliable: Hadoop automatically maintains multiple copies of data and  \
            automatically redeploys computing tasks based on failures. \
\
Hadoop implements MapReduce, using the Hadoop Distributed File System (HDFS). \
MapReduce divides applications into many small blocks of work. HDFS creates \
multiple replicas of data blocks for reliability, placing them on compute \
nodes around the cluster. MapReduce can then process the data where it is \
located.\
\
Hadoop has been demonstrated on clusters with 2000 nodes. The current \
design target is 10,000 node clusters.

%description 
%{global_description}

# List of packages which do not depend on any particular architecture
%ifarch noarch

%package namenode
Summary: The Hadoop namenode manages the block locations of HDFS files
Group: System/Daemons
Requires: hadoop-common = @RPMVERSION@

%description namenode
The Hadoop Distributed Filesystem (HDFS) requires one unique server, the
namenode, which manages the block locations of files on the filesystem.

%{global_description}


%package secondarynamenode
Summary: Hadoop Secondary namenode
Group: System/Daemons
Requires: hadoop-common = @RPMVERSION@

%description secondarynamenode
A Hadoop Distributed Filesystem (HDFS) requires one unique server, the 
namenode. This is a single point of failure for an HDFS installation. 
If the namenode goes down, the entire filesystem is offline. To reduce 
the impact of such an event, the secondarynamenode is used for failover.

%{global_description}


%package jobtracker
Summary: Hadoop Job Tracker
Group: System/Daemons
Requires: hadoop-common = @RPMVERSION@

%description jobtracker
The jobtracker is a central service which is responsible for managing
the tasktracker services running on all nodes in a Hadoop Cluster.
The jobtracker allocates work to the tasktracker nearest to the data
with an available work slot.

%{global_description}


%package datanode
Summary: Hadoop Data Node
Group: System/Daemons
Requires: hadoop-common = @RPMVERSION@

%description datanode
The Data Nodes in the Hadoop Cluster are responsible for serving up
blocks of data over the network to Hadoop Distributed Filesystem
(HDFS) clients.

%{global_description}


%package tasktracker
Summary: Hadoop Task Tracker
Group: System/Daemons
Requires: hadoop-common = @RPMVERSION@

%description tasktracker
The tasktracker has a fixed number of work slots.  The jobtracker
assigns MapReduce work to the tasktracker that is nearest the data
with an available work slot.

%{global_description}


%package docs
Summary: Hadoop Documentation
Group: Documentation
Prefix: %{doc_hadoop}

%description docs
Documentation for Hadoop

%{global_description}

%package common
Summary: Common files (e.g. jars) needed by all Hadoop Services and Clients
Group: Development/Libraries
Prefix: %{opt_hadoop}

%description common
Common files (e.g. jars) needed by all Hadoop Services and Clients

%{global_description}

# All architecture specific packages should follow here inside this else block
%else

%package native
Summary: Native libraries for Hadoop (e.g. compression, Hadoop pipes)
Group: Development/Libraries
Prefix: %{opt_hadoop}
Requires: hadoop-common = @RPMVERSION@

%description native
Native libraries for Hadoop (e.g. compression, Hadoop pipes)

%{global_description}

%endif


#########################
#### INSTALL SECTION ####
#########################
%install
%__rm -rf $RPM_BUILD_ROOT

%__install -d -m 0755 $RPM_BUILD_ROOT/%{opt_hadoop}

%ifarch noarch
# Init.d scripts
%__install -d -m 0755 $RPM_BUILD_ROOT/etc/init.d/
%__cp @PKGROOT@/pkg_scripts/rpm/* $RPM_BUILD_ROOT/etc/init.d/

# Docs
%__install -d -m 0755 $RPM_BUILD_ROOT/%{doc_hadoop}
(cd %{hadoop_build_path}/docs && tar -cf - .) | (cd $RPM_BUILD_ROOT/%{doc_hadoop} && tar -xf -)

# The whole deal...
(cd %{hadoop_build_path} && tar -cf - .) | (cd $RPM_BUILD_ROOT/%{opt_hadoop} && tar -xf -)
# Take out the docs...
rm -rf $RPM_BUILD_ROOT/%{opt_hadoop}/docs
# Take out the native libraries...
rm -rf $RPM_BUILD_ROOT/%{opt_hadoop}/lib/native
%endif

# TODO: clean up this copy/paste
%ifarch i386
%__install -d -m 0755 $RPM_BUILD_ROOT/%{opt_hadoop}/lib/native
(cd %{hadoop_build_path}/lib/native && cp -r ./Linux-i386-32 $RPM_BUILD_ROOT/%{opt_hadoop}/lib/native)
%endif
%ifarch amd64
%__install -d -m 0755 $RPM_BUILD_ROOT/%{opt_hadoop}/lib/native
(cd %{hadoop_build_path}/lib/native && cp -r ./Linux-amd64-64 $RPM_BUILD_ROOT/%{opt_hadoop}/lib/native)
%endif

 

###########################
###### FILES SECTION ######
###########################
%ifarch noarch
%files namenode
%defattr(-,root,root)
/etc/init.d/hadoop-namenode

%files datanode
%defattr(-,root,root)
/etc/init.d/hadoop-datanode

%files jobtracker
%defattr(-,root,root)
/etc/init.d/hadoop-jobtracker

%files tasktracker
%defattr(-,root,root)
/etc/init.d/hadoop-tasktracker

%files secondarynamenode
%defattr(-,root,root)
/etc/init.d/hadoop-secondarynamenode

%files docs
%defattr(-,root,root)
%{doc_hadoop}

%files common
%defattr(-,root,root)
%{opt_hadoop}

# non-noarch files (aka architectural specific files)
%else
%files native
%defattr(-,root,root)
%{opt_hadoop}

%endif
