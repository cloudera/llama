from check_cdh_message import check_match
import unittest

class TestCheckMatch(unittest.TestCase):

  def test_empty_message(self):
    self.assertFalse(check_match(""))

  def test_valid_message(self):
	self.assertTrue(check_match("""HDFS-464. Memory leaks in libhdfs

Description: Memory leaks can occur in several libhdfs calls (e.g. hdfsExists).
Reason: Customer request
Author: Joe Bloggs
Ref: CDH-565"""))

  def test_valid_message_no_description(self):
	self.assertTrue(check_match("""HDFS-464. Memory leaks in libhdfs

Reason: Customer request
Author: Joe Bloggs
Ref: CDH-565"""))

  def test_valid_message_multiline_description(self):
	self.assertTrue(check_match("""HDFS-464. Memory leaks in libhdfs

Description: Memory leaks can occur in several
libhdfs calls (e.g. hdfsExists).
Reason: Customer request
Author: Joe Bloggs
Ref: CDH-565"""))

  def test_valid_message_extra_fields(self):
	self.assertTrue(check_match("""HDFS-464. Memory leaks in libhdfs

Description: Memory leaks can occur in several libhdfs calls (e.g. hdfsExists).
Reason: Customer request
Author: Joe Bloggs
Ref: CDH-565
More stuff here"""))

  def test_valid_message_internal(self):
	self.assertTrue(check_match("""CLOUDERA-BUILD. Fix build system.

Reason: Customer request
Author: Joe Bloggs
Ref: CDH-0"""))

  def test_invalid_message_missing_reason(self):
	self.assertFalse(check_match("""HDFS-464. Memory leaks in libhdfs

Author: Joe Bloggs
Ref: CDH-565"""))

  def test_invalid_message_missing_author(self):
	self.assertFalse(check_match("""HDFS-464. Memory leaks in libhdfs

Reason: Customer request
Ref: CDH-565"""))

  def test_invalid_message_missing_ref(self):
	self.assertFalse(check_match("""HDFS-464. Memory leaks in libhdfs

Description: Memory leaks can occur in several libhdfs calls (e.g. hdfsExists).
Reason: Customer request
Author: Joe Bloggs"""))

  def test_invalid_message_bad_ref(self):
	self.assertFalse(check_match("""HDFS-464. Memory leaks in libhdfs

Description: Memory leaks can occur in several libhdfs calls (e.g. hdfsExists).
Reason: Customer request
Author: Joe Bloggs
Ref: HDFS-464"""))

if __name__ == '__main__':
  unittest.main()