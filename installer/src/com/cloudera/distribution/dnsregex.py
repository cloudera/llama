# (c) Copyright 2008 Cloudera, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# module: com.cloudera.distribution.dnsregex
#
# Defines regular expressions that match DNS and IP addresses
# based on RFC 1035. These expressions aren't actually 100% strict

import re

# regex for IP address: between 1 and 4 dot-separated numeric groups
# which can be between 1 and 10 digits each (this is slightly more
# liberal than required; but this captures dotted quads and dotted halves
# and a single 32-bit value... and some other non-RFC-1035-compliant
# values.

ipAddrRegexStr = "[0-9]{1,10}(\.[0-9]{1,10}){0,3}"
ipAddrRegex = re.compile(ipAddrRegexStr)

# regex for DNS addresses; this is slightly more liberal than RFC 1035
dnsComponentStr = "[A-Za-z][0-9A-Za-z_-]*"
dnsNameRegexStr = dnsComponentStr + "(\." + dnsComponentStr + ")*"
dnsNameRegex = re.compile(dnsNameRegexStr)

dnsNameAndPortStr = dnsNameRegexStr + "\:[0-9]+"
dnsNameAndPortRegex = re.compile(dnsNameAndPortStr)

### public interface below this line ###

def getIpAddrRegexStr():
  """ return the string used to compile the regex """
  global ipAddrRegexStr
  return ipAddrRegexStr

def getIpAddrRegex():
  """ Return a regular expression which will match valid IP addresses """
  global ipAddrRegex
  return ipAddrRegex

def getDnsNameRegexStr():
  """ return the string used to compile the regex """
  global dnsNameRegexStr
  return dnsNameRegexStr

def getDnsNameRegex():
  """ Return a regular expression which will match valid DNS names """
  global dnsNameRegex
  return dnsNameRegex

def isIpAddress(addr):
  """ Return True if 'addr' is a valid IP address """
  m = getIpAddrRegex().match(addr)
  return m != None and m.start() == 0 and m.end() == len(addr)

def isDnsName(name):
  """ Return True if 'name' is a valid DNS address """
  m =  getDnsNameRegex().match(name)
  return m != None and m.start() == 0 and m.end() == len(name)

def getDnsNameAndPortRegexStr():
  global dnsNameAndPortStr
  return dnsNameAndPortStr

def getDnsNameAndPortRegex():
  global dnsNameAndPortRegex
  return dnsNameAndPortRegex

def isDnsNameAndPort(addr):
  """ Matches dnsname:portnum """
  m = getDnsNameAndPortRegex().match(addr)
  return m != None and m.start() == 0 and m.end() == len(addr)


