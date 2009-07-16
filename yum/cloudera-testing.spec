Summary: Cloudera's Distribution for Hadoop testing repository
Name: cloudera-testing 
Version: 0.1.0
Release: 1
License: Apache License v2.0
Group: System Environment/Base
URL: http://cloudera.com/

Packager: Matt Massie <matt@cloudera.com>
Vendor: Cloudera Yum Repository, http://archive.cloudera.com/testing/

Source0: mirrors-cloudera-testing
Source1: RPM-GPG-KEY-cloudera-testing
Source2: cloudera-testing.repo
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root

%description
This packages contains the yum configuration for the "testing"
repository of Cloudera's Distribution for Hadoop including the
public keys used to sign the RPMs.

%prep
%setup -c

%build

%install
%{__rm} -rf %{buildroot}
# Copy into build directory for %pubkey macro
%{__cp} $RPM_SOURCE_DIR/RPM-GPG-KEY-cloudera-testing .
%{__install} -Dp -m0644 $RPM_SOURCE_DIR/RPM-GPG-KEY-cloudera-testing %{buildroot}%{_sysconfdir}/pki/rpm-gpg/RPM-GPG-KEY-cloudera-testing
%{__install} -Dp -m0644 $RPM_SOURCE_DIR/cloudera-testing.repo        %{buildroot}%{_sysconfdir}/yum.repos.d/cloudera-testing.repo
%{__install} -Dp -m0644 $RPM_SOURCE_DIR/mirrors-cloudera-testing     %{buildroot}%{_sysconfdir}/yum.repos.d/mirrors-cloudera-testing 

%clean
#%{__rm} -rf %{buildroot}

%post
rpm -q gpg-pubkey-e8f86acd-4a418045 || rpm --import %{_sysconfdir}/pki/rpm-gpg/RPM-GPG-KEY-cloudera-testing || :

%files
%defattr(-, root, root, 0755)
%pubkey RPM-GPG-KEY-cloudera-testing
%dir %{_sysconfdir}/yum.repos.d/
%config(noreplace) %{_sysconfdir}/yum.repos.d/cloudera-testing.repo
%config(noreplace) %{_sysconfdir}/yum.repos.d/mirrors-cloudera-testing
%dir %{_sysconfdir}/pki/rpm-gpg/
%{_sysconfdir}/pki/rpm-gpg/RPM-GPG-KEY-cloudera-testing

%changelog
* Wed Jun 24 2009 Matt Massie <matt@cloudera.com>
- Initial version. 
