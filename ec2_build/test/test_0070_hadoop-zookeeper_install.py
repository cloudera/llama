#!/usr/bin/python

import sys
from cloudera.packaging.packaging import PackageManagerFactory

PACKAGE_HADOOP_ZOOKEEPER_NAME = "hadoop-zookeeper"
PACKAGE_HADOOP_ZOOKEEPER_SERVEUR_NAME = "hadoop-zookeeper-server"


def main():
  packageManagerFactory = PackageManagerFactory()

  packageManager = packageManagerFactory.create_package_manager()

  zookeeper = packageManager.search_from_name(PACKAGE_HADOOP_ZOOKEEPER_NAME)
  zookeeper_server = packageManager.search_from_name(PACKAGE_HADOOP_ZOOKEEPER_SERVEUR_NAME)

  if len(zookeeper) == 0:
     raise Exception(PACKAGE_HADOOP_ZOOKEEPER_NAME + " not available")

  if len(zookeeper_server) == 0:
     raise Exception(PACKAGE_HADOOP_ZOOKEEPER_SERVEUR_NAME + " not available")

  packages = []
  packages.extend(zookeeper)
  packages.extend(zookeeper_server)
  packageManager.install(packages)

  for package in packages:
    print package.name()
    print package.files()
    print packageManager.is_package_installed(package.name())


  packageManager.uninstall(packages)

  print "Exiting script."
  sys.exit(0)

main()
