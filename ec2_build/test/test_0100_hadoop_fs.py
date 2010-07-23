#!/usr/bin/python

import sys
import unittest
from cloudera.packaging.packaging import PackageManagerFactory
import cloudera.utils


class hadoop_fs(unittest.TestCase):

  PACKAGE_HADOOP_NAMES = ['hadoop-0.20.noarch', 'hadoop-0.20-conf-pseudo.noarch',
                          'hadoop-0.20-datanode.noarch', 'hadoop-0.20-docs.noarch',
                          'hadoop-0.20-jobtracker.noarch', 'hadoop-0.20-libhdfs',
                          'hadoop-0.20-namenode.noarch', 'hadoop-0.20-native',
                          'hadoop-0.20-pipes', 'hadoop-0.20-secondarynamenode.noarch',
                          'hadoop-0.20-tasktracker.noarch']

  def setUp(self):
    packageManagerFactory = PackageManagerFactory()
    self.packageManager = packageManagerFactory.create_package_manager()

    self.hadoop_packages = []
    for pkg_name in self.PACKAGE_HADOOP_NAMES:
      self.hadoop_packages.extend(self.packageManager.search_from_name(pkg_name))

    self.packageManager.install(self.hadoop_packages)

  def test_simple_start_name_node(self):
    (return_code, stdout) = cloudera.utils.simple_exec2(["/etc/init.d/hadoop-0.20-namenode", "start"])

    self.assertEqual(return_code, 0, 'hadoop namenode failed to start')

  def test_simple_start_data_node(self):
    (return_code, stdout) = cloudera.utils.simple_exec2(["/etc/init.d/hadoop-0.20-datanode", "start"])

    self.assertEqual(return_code, 0, 'hadoop datanode failed to start')

  def test_simple_ls(self):
    (return_code, stdout) = cloudera.utils.simple_exec2(["hadoop", "fs", "-ls", "/"])

    self.assertEqual(return_code, 0, 'hadoop ls failed')

  def tearDown(self):
    self.packageManager.uninstall(self.hadoop_packages)


def build_suite():
    suite = unittest.TestSuite()
    suite.addTest(hadoop_fs('test_simple_start_name_node'))
    suite.addTest(hadoop_fs('test_simple_start_data_node'))
    suite.addTest(hadoop_fs('test_simple_ls'))
    return suite

if __name__ == '__main__':
    suite = build_suite()
    unittest.TextTestRunner(verbosity=2).run(suite)



