# Copyright (c) 2010 Cloudera, inc.


# Cloudera network
CLOUDERA_NETWORK = '38.102.147.0/24'

# Public IP of archive.cloudera.com
OFFICIAL_ARCHIVE_IP_ADDRESS = '184.73.170.21'

# Public IP of nightly.cloudera.com
# NIGHTLY_ARCHIVE_IP_ADDRESS = '184.73.215.26'
NIGHTLY_ARCHIVE_IP_ADDRESS = '50.16.200.36'


# OS using sudo for root operations
OS_USING_SUDO = ['ubuntu']


class RepositoryType:
  APT = 'apt'
  YUM = 'yum'


GPG_KEY_FINGERPRINT_FOR_REPOSITORY_KIND = {
        RepositoryType.APT: 'F36A89E33CC1BD0F71079007327574EE02A818DD',
        RepositoryType.YUM: '5F14D39EF0681ACA6F044A43F90C0D8FE8F86ACD'}

# Key name of the maintener for yum repositories
YUM_MAINTENER_GPG_KEY = 'Yum Maintainer'
