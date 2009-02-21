# 
# Hadoop RPM spec file 
# 
%define hadoop_name hadoop
%define etc_hadoop %{_sysconfdir}/%{hadoop_name}
%define config_hadoop %{etc_hadoop}/config
%define lib_hadoop %{_libdir}/%{hadoop_name}
%define log_hadoop %{_localstatedir}/log/%{hadoop_name}
%define bin_hadoop %{_bindir}
%define doc_hadoop /usr/share/doc/hadoop-@VERSION@
%define hadoop_build_path @PKGROOT@/build/hadoop-@VERSION@
%define hadoop_username hadoop
%define hadoop_services namenode secondarynamenode datanode jobtracker tasktracker

Name: %{hadoop_name} 
Vendor: Hadoop
Version: @RPMVERSION@
Release: @RELEASE@
Summary: Hadoop is a software platform that lets one easily write and run applications that process vast amounts of data. 
License: Apache License v2.0
URL: http://hadoop.apache.org/core/
Group: Development/Libraries
Buildroot: @RPMBUILDROOT@
Prereq: sh-utils, textutils, /usr/sbin/useradd, /sbin/chkconfig, /sbin/service
Provides: %{hadoop_name} %{config_hadoop}
Summary: Standalone installation of Hadoop

%description 
Hadoop is a software platform that lets one easily write and  
run applications that process vast amounts of data. 

Here's what makes Hadoop especially useful: 
* Scalable: Hadoop can reliably store and process petabytes. 
* Economical: It distributes the data and processing across clusters  
              of commonly available computers. These clusters can number  
              into the thousands of nodes. 
* Efficient: By distributing the data, Hadoop can process it in parallel  
             on the nodes where the data is located. This makes it  
             extremely rapid. 
* Reliable: Hadoop automatically maintains multiple copies of data and  
            automatically redeploys computing tasks based on failures. 

Hadoop implements MapReduce, using the Hadoop Distributed File System (HDFS). 
MapReduce divides applications into many small blocks of work. HDFS creates 
multiple replicas of data blocks for reliability, placing them on compute 
nodes around the cluster. MapReduce can then process the data where it is 
located.


%ifarch noarch

%package namenode
Summary: The Hadoop namenode manages the block locations of HDFS files
Group: System/Daemons
Requires: hadoop = @RPMVERSION@

%description namenode
The Hadoop Distributed Filesystem (HDFS) requires one unique server, the
namenode, which manages the block locations of files on the filesystem.


%package secondarynamenode
Summary: Hadoop Secondary namenode
Group: System/Daemons
Requires: hadoop = @RPMVERSION@

%description secondarynamenode
The Secondary Name Node periodically compacts the Name Node EditLog
into a checkpoint.  This compaction ensures that Name Node restarts
do not incur unnecessary downtime.


%package jobtracker
Summary: Hadoop Job Tracker
Group: System/Daemons
Requires: hadoop = @RPMVERSION@

%description jobtracker
The jobtracker is a central service which is responsible for managing
the tasktracker services running on all nodes in a Hadoop Cluster.
The jobtracker allocates work to the tasktracker nearest to the data
with an available work slot.


%package datanode
Summary: Hadoop Data Node
Group: System/Daemons
Requires: hadoop = @RPMVERSION@

%description datanode
The Data Nodes in the Hadoop Cluster are responsible for serving up
blocks of data over the network to Hadoop Distributed Filesystem
(HDFS) clients.


%package tasktracker
Summary: Hadoop Task Tracker
Group: System/Daemons
Requires: hadoop = @RPMVERSION@

%description tasktracker
The tasktracker has a fixed number of work slots.  The jobtracker
assigns MapReduce work to the tasktracker that is nearest the data
with an available work slot.


%package conf-pseudo
Summary: Hadoop installation in pseudo-distributed mode
Group: System/Daemons
Requires: hadoop = @RPMVERSION@
Provides: %{config_hadoop}

%description conf-pseudo
Installation of this RPM will setup your machine to run in pseudo-distributed mode
where each Hadoop daemon runs in a separate Java process.

%package docs
Summary: Hadoop Documentation
Group: Documentation
Prefix: %{doc_hadoop}

%description docs
Documentation for Hadoop

# All architecture specific packages should follow here inside this else block
%else

%package native
Summary: Native libraries for Hadoop (e.g., compression, Hadoop pipes)
Group: Development/Libraries
Prefix: %{lib_hadoop}
Requires: hadoop = @RPMVERSION@

%description native
Native libraries for Hadoop (e.g., compression, Hadoop pipes).

%endif


#########################
#### INSTALL SECTION ####
#########################
%install
%__rm -rf $RPM_BUILD_ROOT

%__install -d -m 0755 $RPM_BUILD_ROOT/%{lib_hadoop}

%ifarch noarch
# man page
%__install -d -m 0755 $RPM_BUILD_ROOT/%{_mandir}/man1/
cp @PKGROOT@/pkg_scripts/rpm/hadoop.1.gz $RPM_BUILD_ROOT/%{_mandir}/man1/
# Init.d scripts
%__install -d -m 0755 $RPM_BUILD_ROOT/etc/rc.d/init.d/
# For the common-only installation
%__install -d -m 0755 $RPM_BUILD_ROOT/%{etc_hadoop}/conf.empty
(cd %{hadoop_build_path}/conf && tar -cf - .) | (cd $RPM_BUILD_ROOT/%{etc_hadoop}/conf.empty && tar -xf -)
# Generate the init.d scripts
for service in %{hadoop_services}
do
       init_file=$RPM_BUILD_ROOT/etc/rc.d/init.d/hadoop-${service}
       %__cp @PKGROOT@/pkg_scripts/rpm/hadoop-init.tmpl $init_file 
       %__sed -i -e 's|@HADOOP_USERNAME@|%{hadoop_username}|' $init_file
       %__sed -i -e 's|@HADOOP_COMMON_ROOT@|%{lib_hadoop}|' $init_file
       %__sed -i -e "s|@HADOOP_DAEMON@|${service}|" $init_file
       %__sed -i -e 's|@HADOOP_CONF_DIR@|%{config_hadoop}|' $init_file
       chmod 755 $init_file
done

# For the pseudo-distributed installation
%__install -d -m 0755 $RPM_BUILD_ROOT/etc/hadoop/conf.pseudo
(cd %{hadoop_build_path}/conf && tar -cf - .) | (cd $RPM_BUILD_ROOT/%{etc_hadoop}/conf.pseudo && tar -xf -)
# Overwrite the hadoop-site.xml with our special pseudo-distributed one
%__cp @PKGROOT@/pkg_scripts/rpm/hadoop-site-pseudo.xml $RPM_BUILD_ROOT/%{etc_hadoop}/conf.pseudo/hadoop-site.xml

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
%__sed -i -e 's|@HADOOP_CONF_DIR@|%{config_hadoop}|' $hadoop_config
%__sed -i -e 's|@HADOOP_LOG_DIR@|%{log_hadoop}|' $hadoop_config
%__cp $hadoop_config $RPM_BUILD_ROOT/%{bin_hadoop}
# /var/lib/hadoop/cache
%__install -d -m 0755 $RPM_BUILD_ROOT/var/lib/hadoop/cache
# /var/log/hadoop
%__install -d -m 0600 $RPM_BUILD_ROOT/%{log_hadoop}
 
%endif

%ifarch i386
%__install -d -m 0755 $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native
(cd %{hadoop_build_path}/lib/native && cp -r ./Linux-i386-32 $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native)
%endif
%ifarch amd64
%__install -d -m 0755 $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native
(cd %{hadoop_build_path}/lib/native && cp -r ./Linux-amd64-64 $RPM_BUILD_ROOT/%{lib_hadoop}/lib/native)
%endif

%ifarch noarch

%files docs
%defattr(-,root,root)
%doc %{doc_hadoop}

%pre 
/usr/sbin/useradd -c "Hadoop" -s /sbin/nologin -r -d / %{hadoop_username} 2> /dev/null || :

%post 
%{_sbindir}/alternatives --install %{config_hadoop} hadoop %{etc_hadoop}/conf.empty 10

%preun 
if [ "$1" = 0 ]; then
	%{_sbindir}/alternatives --remove hadoop %{etc_hadoop}/conf.empty
fi

%files 
%defattr(-,root,root)
%attr(0755,root,root)/etc/rc.d/init.d/hadoop-namenode
%attr(0755,root,root)/etc/rc.d/init.d/hadoop-datanode
%attr(0755,root,root)/etc/rc.d/init.d/hadoop-jobtracker
%attr(0755,root,root)/etc/rc.d/init.d/hadoop-tasktracker
%attr(0755,root,root)/etc/rc.d/init.d/hadoop-secondarynamenode
%config %attr(755,hadoop,hadoop) %{etc_hadoop}/conf.empty
%{lib_hadoop}
%attr(0755,root,root) %{bin_hadoop}/hadoop
%attr(0755,root,root) %{bin_hadoop}/hadoop-config.sh
%{_mandir}/man1/hadoop.1.gz
%attr(0700,hadoop,hadoop) %{log_hadoop}

# Service file management RPMs
%define service_macro() \
%files %1 \
%attr(0755,root,root)/etc/rc.d/init.d/hadoop-%1 \
%{lib_hadoop}/bin/hadoop-daemon.sh \
%{bin_hadoop}/hadoop-config.sh \
%post %1 \
chkconfig --add hadoop-%1 \
\
%preun %1 \
if [ $1 = 0 ]; then \
	service hadoop-%1 stop > /dev/null \
	chkconfig --del hadoop-%1 \
fi
%service_macro namenode
%service_macro secondarynamenode
%service_macro datanode
%service_macro jobtracker
%service_macro tasktracker

# Pseudo-distributed Hadoop installation
%post conf-pseudo
%{_sbindir}/alternatives --install %{config_hadoop} hadoop %{etc_hadoop}/conf.pseudo 30
nn_dfs_dir="/var/lib/hadoop/cache/hadoop/dfs"
if [ -z "$(ls -A $nn_dfs_dir 2>/dev/null)" ]; then
	/sbin/runuser -s /bin/bash - %{hadoop_username} -c 'hadoop namenode -format'
fi

%preun conf-pseudo
if [ "$1" = 0 ]; then
        %{_sbindir}/alternatives --remove hadoop %{etc_hadoop}/conf.pseudo
fi

%files conf-pseudo
%config %attr(755,hadoop,hadoop) %{etc_hadoop}/conf.pseudo
%attr(0755,hadoop,hadoop) /var/lib/hadoop
%attr(1777,hadoop,hadoop) /var/lib/hadoop/cache

# non-noarch files (aka architectural specific files)
%else
%files native
%defattr(-,root,root)
%{lib_hadoop}/lib/native

%endif
