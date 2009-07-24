#!/usr/bin/env python2.5
# (c) Copyright 2009 Cloudera, Inc.

import re
import subprocess
import unittest

GIT = "git"

def git(cmdv):
  """ Run a git command. Raise exception if it has a nonzero return code. """
  p = subprocess.Popen([GIT] + cmdv, stdout=subprocess.PIPE)
  stdout, stderr = p.communicate()
  if p.returncode != 0:
    raise Exception("Error executing %s: %s" % (" ".join(cmdv), stderr))
  return stdout.strip()

def git_merge_base(a, b):
  return git(["merge-base", a, b])

def git_rev_list(a, b):
  res = git(["rev-list", a + '..' + b])
  if res == "":
    return []
  return res.split("\n")

def git_rev_parse(r):
  return git(['rev-parse', '--verify', '--quiet', r])

def git_cur_branch():
  try:
    ret = git(['symbolic-ref', '--quiet', 'HEAD'])
    if ret.startswith("refs/heads/"):
      return re.sub(r'^refs/heads/', '', ret)
    return None
  except:
    return None

def count_commits_from(from_rev, to_rev):
  """Return the number of commits from from_rev to to_rev"""
  return len(git_rev_list(from_rev, to_rev))

def cdh_best_branch(r):
  """
  Return the name of the cdh branch that the given commit
  is best described by. This means the shortest version number
  of a branch which is a descendent of this one.
  """
  branches = git(['branch', '-a', '--contains', r]).split("\n")
  branches = [b.lstrip('*').strip() for b in branches]
  branches = [re.sub(r'^.+/(.+)$', r'\1', b) for b in branches]
  branches = [b for b in branches if b.startswith('cdh-')]

  # The best branch is the one with the shortest version number
  branches.sort(cmp=lambda a,b: len(a) - len(b))

  return branches[0]

def cdh_ancestor_branch(branch, apache_remote):
  """
  Determine the ancestor branch of the given branch name.
  If the current branch is like cdh-a.b+c.d then will chop
  the last component (result cdh-a.b+c). If it is like
  cdh-a.b with no + segment, it will go to the relevant
  apache release.

  See unit tests for some examples.
  """
  if not branch.startswith('cdh-'):
    raise Exception("Not a cdh-* branch: %s" % branch)
  
  m = re.match(r'cdh-([\d\.]+)$', branch)
  if m:
    return "%s/tags/release-%s" % (apache_remote, m.group(1))

  return re.sub(r'[\.\+][^\.\+]+?$', '', branch)

def cdh_get_version(rev, apache_remote):
  """ Given a revision, determine the unique version number for it. """

  if rev.startswith('cdh-'):
    cur_branch = rev
  else:
    cur_branch = cdh_best_branch(rev)
  assert cur_branch.startswith("cdh-")
  base_version = re.sub(r'^cdh-', '', cur_branch)
  ancestor = cdh_ancestor_branch(cur_branch, apache_remote)
  merge_base = git_merge_base(rev, ancestor)
  count = count_commits_from(merge_base, rev)

  if ancestor.startswith('apache'):
    return base_version + '+' + str(count)
  else:
    return base_version + '.' + str(count)


class Test(unittest.TestCase):
  def testAncestor(self):
    self.assertEquals(cdh_ancestor_branch('cdh-0.18.3'), 'apache/tags/release-0.18.3')
    self.assertEquals(cdh_ancestor_branch('cdh-0.18.3+3'), 'cdh-0.18.3')
    self.assertEquals(cdh_ancestor_branch('cdh-0.18.3+3.4'), 'cdh-0.18.3+3')
    self.assertEquals(cdh_ancestor_branch('cdh-0.20.0'), 'apache/tags/release-0.20.0')
