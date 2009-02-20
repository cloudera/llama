# 
# Hadoop RPM spec file 
# 
%define hadoop_name hadoop
%define etc_hadoop %{_sysconfdir}/%{hadoop_name}
%define lib_hadoop %{_libdir}/%{hadoop_name}
%define log_hadoop %{_localstatedir}/log/%{hadoop_name}
%define bin_hadoop %{_bindir}
%define doc_hadoop /usr/share/doc/hadoop-@VERSION@
%define hadoop_build_path @PKGROOT@/build/hadoop-@VERSION@
%define hadoop_username hadoop

Name: %{hadoop_name} 
Vendor: Hadoop
Version: @RPMVERSION@
Release: @RELEASE@
Summary: Hadoop is a software platform that lets one easily write and run applications that process vast amounts of data. 
License: Apache License v2.0
URL: http://hadoop.apache.org/core/
Group: System/Daemons
Buildroot: @RPMBUILDROOT@
Prereq: sh-utils, textutils, /usr/sbin/useradd, /sbin/chkconfig, /sbin/service
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
located.

%description 
%{global_description}

# List of packages which do not depend on any particular architecture
%ifarch noarch

%package pseudo
Summary: Hadoop installation in pseudo-distributed mode
Group: System/Daemons
Requires: hadoop-namenode hadoop-datanode hadoop-jobtracker hadoop-tasktracker

%description pseudo
Installation of this RPM will setup your machine to run in pseudo-distributed mode
where each Hadoop daemon runs in a separate Java process.

%{global_description}


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
The Secondary Name Node periodically compacts the Name Node EditLog
into a checkpoint.  This compaction ensures that Name Node restarts
do not incur unnecessary downtime.

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
Summary: Common files (e.g., jars) needed by all Hadoop Services and Clients
Group: Development/Libraries
Prefix: %{lib_hadoop}

%description common
Common files (e.g., jars) needed by all Hadoop Services and Clients

%{global_description}

# All architecture specific packages should follow here inside this else block
%else

%package native
Summary: Native libraries for Hadoop (e.g., compression, Hadoop pipes)
Group: Development/Libraries
Prefix: %{lib_hadoop}
Requires: hadoop-common = @RPMVERSION@

%description native
Native libraries for Hadoop (e.g., compression, Hadoop pipes)

%{global_description}

%endif


#########################
#### INSTALL SECTION ####
#########################
%install
%__rm -rf $RPM_BUILD_ROOT

%__install -d -m 0755 $RPM_BUILD_ROOT/%{lib_hadoop}

%ifarch noarch
# Init.d scripts
%__install -d -m 0755 $RPM_BUILD_ROOT/etc/rc.d/init.d/
services="datanode jobtracker namenode secondarynamenode tasktracker"
for service in $services; 
do
	init_file=$RPM_BUILD_ROOT/etc/rc.d/init.d/hadoop-${service}
	%__cp @PKGROOT@/pkg_scripts/rpm/hadoop-init.tmpl $init_file 
	%__sed -i -e 's|@HADOOP_USERNAME@|%{hadoop_username}|' $init_file
	%__sed -i -e 's|@HADOOP_PREFIX@|%{lib_hadoop}|' $init_file
	%__sed -i -e "s|@HADOOP_DAEMON@|${service}|" $init_file
	%__sed -i -e "s|@HADOOP_PROG@|hadoop-${service}|" $init_file
	%__sed -i -e 's|@HADOOP_CONF_DIR@|%{etc_hadoop}|' $init_file
	chmod 755 $init_file
done

# Logs
%__install -d -m 0755 $RPM_BUILD_ROOT/%{log_hadoop}

# Docs
%__install -d -m 0755 $RPM_BUILD_ROOT/%{doc_hadoop}
(cd %{hadoop_build_path}/docs && tar -cf - .) | (cd $RPM_BUILD_ROOT/%{doc_hadoop} && tar -xf -)

# The whole deal...
(cd %{hadoop_build_path} && tar -cf - .) | (cd $RPM_BUILD_ROOT/%{lib_hadoop} && tar -xf -)

# Take out the docs...
%__rm -rf $RPM_BUILD_ROOT/%{lib_hadoop}/docs
# Take out the native libraries...
%__rm -rf $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native
# Take out the src...
%__rm -rf $RPM_BUILD_ROOT/%{lib_hadoop}/src
# Take out the configuration...
%__rm -rf $RPM_BUILD_ROOT/%{lib_hadoop}/conf

# Setup hadoop and hadoop-config.sh in bin
%__install -d -m 0755 $RPM_BUILD_ROOT/%{bin_hadoop}
# First, lets point all the bin scripts to the correct hadoop-config.sh file
for file in $RPM_BUILD_ROOT/%{lib_hadoop}/bin/*
do
	%__sed -i -e 's|^.*hadoop-config.sh.*$|. %{bin_hadoop}/hadoop-config.sh|' $file
	%__sed -i -e 's|"$HADOOP_HOME"/bin/hadoop|%{bin_hadoop}/hadoop|' $file
done
# move hadoop bin
%__mv $RPM_BUILD_ROOT/%{lib_hadoop}/bin/hadoop $RPM_BUILD_ROOT/%{bin_hadoop}
# remove the standard hadoop-config.sh file
%__rm $RPM_BUILD_ROOT/%{lib_hadoop}/bin/hadoop-config.sh
# copy in our new and improved hadoop-config.sh
hadoop_config=@PKGROOT@/pkg_scripts/rpm/hadoop-config.sh
%__sed -i -e 's|@HADOOP_HOME@|%{lib_hadoop}|' $hadoop_config 
%__sed -i -e 's|@HADOOP_CONF_DIR@|%{etc_hadoop}|' $hadoop_config
%__sed -i -e 's|@HADOOP_LOG_DIR@|%{log_hadoop}|' $hadoop_config
%__cp $hadoop_config $RPM_BUILD_ROOT/%{bin_hadoop}
 
# Configuration
%__install -d -m 0755 $RPM_BUILD_ROOT/%{etc_hadoop}
(cd %{hadoop_build_path}/conf && tar -cf - .) | (cd $RPM_BUILD_ROOT/%{etc_hadoop} && tar -xf -)
%__rm $RPM_BUILD_ROOT/%{etc_hadoop}/hadoop-site.xml
%__cp @PKGROOT@/pkg_scripts/rpm/hadoop-site-pseudo.xml $RPM_BUILD_ROOT/%{etc_hadoop}/hadoop-site.xml
#hadoop_env=$RPM_BUILD_ROOT/%{etc_hadoop}/hadoop-env.sh
# Point to the correct log directory
#%__sed -i -e 's|^.*export HADOOP_LOG_DIR.*$|export HADOOP_LOG_DIR="%{log_hadoop}"|' $hadoop_env
# Point to the correct JAVA_HOME
#%__sed -i -e 's|^.*export JAVA_HOME.*$|export JAVA_HOME="/usr/java/default"|' $hadoop_env

%__install -d -m 0755 $RPM_BUILD_ROOT/var/lib/hadoop/cache
 
%endif

%ifarch i386
%__install -d -m 0755 $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native
(cd %{hadoop_build_path}/lib/native && cp -r ./Linux-i386-32 $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native)
%endif
%ifarch amd64
%__install -d -m 0755 $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native
(cd %{hadoop_build_path}/lib/native && cp -r ./Linux-amd64-64 $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native)
%endif

%define useradd_cmd /usr/sbin/useradd -c "Hadoop" -s /sbin/nologin -r -d / %{hadoop_username} 2> /dev/null || :
%define chkconfig_add /sbin/chkconfig --add
%define chkconfig_del /sbin/chkconfig --del

%ifarch noarch
%define service_install() \
%pre %1 \
%{useradd_cmd} \
\
%post %1 \
%{chkconfig_add} hadoop-%1 \
\
%preun %1 \
if [ $1 = 0 ]; then \
        /sbin/service hadoop-%1 stop > /dev/null \
        %{chkconfig_del} hadoop-%1 \
fi \
\
%files %1 \
/etc/rc.d/init.d/hadoop-%1 \
%attr(0700,%{hadoop_username},%{hadoop_username}) %dir %{log_hadoop}

%service_install namenode
%service_install secondarynamenode
%service_install datanode
%service_install jobtracker
%service_install tasktracker

%files docs
%defattr(-,root,root)
%doc %{doc_hadoop}
 
%files common
%defattr(-,root,root)
%{lib_hadoop}
%attr(0755,root,root) %{bin_hadoop}/hadoop
%attr(0755,root,root) %{bin_hadoop}/hadoop-config.sh

%post pseudo
nn_dfs_dir="/var/lib/hadoop/cache/hadoop/dfs"
if [ -z "$(ls -A $nn_dfs_dir 2>/dev/null)" ]; then
	/sbin/runuser -s /bin/bash - %{hadoop_username} -c 'hadoop namenode -format'
fi
/sbin/service hadoop-namenode start
/sbin/service hadoop-datanode start 
/sbin/service hadoop-tasktracker start
/sbin/service hadoop-jobtracker start

%files pseudo
%config %attr(755,hadoop,hadoop) %{etc_hadoop}
%attr(0755,hadoop,hadoop) /var/lib/hadoop
%attr(1777,hadoop,hadoop) /var/lib/hadoop/cache

# non-noarch files (aka architectural specific files)
%else
%files native
%defattr(-,root,root)
%{lib_hadoop}

%endif
