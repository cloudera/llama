#!/usr/bin/env python
# (c) Copyright 2011 Cloudera, Inc.

import os
import os.path
import shutil
import time
from optparse import OptionParser


__usage = """
    --root | -r <dir>       Staging root directory.
    --expr | -e <seconds>   Age in seconds for expiration. Defaults to 86400 (1 week).
    --nightly | -n <string> Prefix for directories which should be deleted. Defaults to "nightly-"
    """

def parse_args():
    """ Parse command line options """

    op = OptionParser(usage = __usage)

    op.add_option('-r', "--root")
    op.add_option('-e', "--expr",
                  default=86400)
    op.add_option('-p', "--prefix",
                  default="nightly-")
    
    opts, args = op.parse_args()
    
    if not opts.root:
        op.print_usage()
        raise Exception("Missing --root.")

    if not os.path.isdir(opts.root):
        op.print_usage()
        raise Exception("--root value %s is not a directory." % (opts.root, ))

    if not type(opts.expr) in (int, float):
        op.print_usage()
        raise Exception("--expr value %s is not a number." % (opts.expr, ))
    
    return opts

    

def delete_builds(staging_root, expr_time=86400, dir_prefix="nightly-"):
    """
    Remove directories under staging_root that are both older than the given expr_time and
    which have names starting with "nightly-".

    @param staging_root Staging root directory.
    @param expr_time Minimum age in seconds for deletion. Defaults to 86400 - i.e., one week.
    """
    for dir_name in os.listdir(staging_root): 
        full_dir = os.path.join(staging_root, dir_name) 
        if not os.path.isdir(full_dir) or \
               not os.stat(full_dir).st_ctime < time.time()-(expr_time) or \
               not dir_name.startswith(dir_prefix): 
            continue
        
        os.chdir(staging_root)
        print "Removing directory %s" % (full_dir, )
        
        shutil.rmtree(full_dir)

    print "All staging directories in %s older than %s deleted." % (staging_root, expr_time)


def main():
    options = parse_args()

    delete_builds(opts.root, opts.expr)
    

if __name__ == "__main__":
  main()
