#!/bin/env python

import re
import sys

def check_match(message):
	pattern = re.compile(r"""
		.*?\n                 # arbitrary - does not check that it contains a JIRA number,
                              #   for example, since there may not be a public JIRA
    	\s*\n                 # blank line
		(Description:.*?\n)?  # optional description
		Reason:.*?\n
		Author:.*?\n
		Ref:\s*CDH-\d+        # reference JIRA must be CDH internal JIRA
		(\n.*)?               # more stuff allowed at the end
""", re.DOTALL | re.VERBOSE)
	return pattern.match(message)
	
if __name__ == '__main__':
	message = "".join(sys.stdin.readlines())
	if not check_match(message):
		print """Invalid format for message:
%s
Please make sure messages are in the following format:
[Apache JIRA number]. [Apache JIRA summary]

Description: [(Optional) Description]
Reason: [Reason for inclusion in CDH]
Author: [Author]
Ref: [CDH number]""" % message
		sys.exit(1)