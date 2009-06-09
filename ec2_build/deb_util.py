#!/usr/bin/env python2.5
# (c) Copyright 2009 Cloudera, Inc.

import os

def find_source_deb_files(root):
  """
  Given a directory 'root', returns the files that correspond to a single
  source deb in that directory.
  """
  changes = [f for f in os.listdir(root) if f.endswith("_source.changes")]
  if len(changes) == 0:
    raise Exception("No source debs found in %s" % root)
  elif len(changes) > 1:
    raise Exception("Multiple source debs found in %s: %s" % (root, repr(changes)))
  changes = changes[0]
  base = changes.replace("_source.changes", "")
  files = [base + ext for ext in ["_source.changes",
                                  ".diff.gz",
                                  ".dsc"]]
  pkg_name, full_vers = base.split("_", 1)
  major_vers, release = full_vers.split("-", 1)
  files.append("%s_%s.orig.tar.gz" % (pkg_name, major_vers))

  files = [os.path.join(root, f) for f in files]
  for f in files:
    if not os.path.exists(f):
      raise Exception("Postcondition error: %s does not exist" % f)
  return files
