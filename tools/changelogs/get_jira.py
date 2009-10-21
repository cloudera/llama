#!/usr/bin/env python2.5
# (c) Copyright 2009 Cloudera, Inc.

from BeautifulSoup import BeautifulSoup
from optparse import OptionParser
import httplib2
import time
import re
import sys

SCRAPING_CONN = httplib2.Http("/tmp/jira-cache")
SCRAPING_DOMAIN_RE = re.compile("\w+:/*(?P<domain>[a-zA-Z0-9.]*)/")
SCRAPING_DOMAINS = {}
SCRAPING_CACHE_FOR = 60 * 60 # cache for 60 minutes
SCRAPING_REQUEST_STAGGER = 1100 # in milliseconds
SCRAPING_CACHE = {}

JIRA_URL = "https://issues.apache.org/jira/si/jira.issueviews:issue-html/%s/%s.html"

def strip_tags(value):
    "Return the given HTML with all tags stripped."
    return re.sub(r'<[^>]*?>', '', value)

def fetch(url, method = "GET"):
    " Return the HTML at the given url. "

    key = (url, method)
    now = time.time()
    if SCRAPING_CACHE.has_key(key):
        data, cached_at = SCRAPING_CACHE[key]
        if now - cached_at < SCRAPING_CACHE_FOR:
            return data
    domain = SCRAPING_DOMAIN_RE.findall(url)[0]
    if SCRAPING_DOMAINS.has_key(domain):
        last_scraped = SCRAPING_DOMAINS[domain]
        elapsed = now - last_scraped
        if elapsed < SCRAPING_REQUEST_STAGGER:
            wait_period = (SCRAPING_REQUEST_STAGGER - elapsed) / 1000
            time.sleep(wait_period)
    SCRAPING_DOMAINS[domain] = time.time()
    data = SCRAPING_CONN.request(url, method)
    SCRAPING_CACHE[key] = (data, now)

    if len(data) > 1:
      return data[1]
    else:
      return ''

def fetch_description(issue_id):
    """ Given an issue id get the description field from the jira."""
    contents = 'No description available.'

    page = fetch(JIRA_URL % (issue_id, issue_id))

    if page:
      soup = BeautifulSoup(page)
      desc = soup.findAll(attrs = {'id': 'descriptionArea'})

      if desc:
        contents = strip_tags(desc[0].renderContents()).strip()

    return contents

def main():
    " Look up and print the description of a given jira."

    usage = "usage: %prog [options] arg"
    parser = OptionParser(usage)
    parser.add_option("-i", "--issue", dest = "issue",
                      help = "jira issue id to look up")

    (options, _) = parser.parse_args()

    if options.issue:
        print fetch_description(options.issue)
    else:
        print "You need to specify a Jira to look up."
        sys.exit(1)

if __name__ == "__main__":
    main()

