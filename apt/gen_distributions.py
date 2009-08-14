#!/usr/bin/env python2.5
# (c) Copyright 2009 Cloudera, Inc.


DISTS=[
  ('lenny', '4.1'),
  ('hardy', '8.04'),
  ('intrepid', '8.10'),
  ('jaunty', '9.04'),
  ]

stanzas = []

for (codename, version) in DISTS:
  stanza = """
Origin: Cloudera
Label: Cloudera
Suite: %(suite)s
Codename: %(codename)s
Version: %(version)s
Architectures: i386 amd64 source
Components: contrib
Description: Cloudera packages for %(codename)s
SignWith: 02A818DD""" % {
    'suite': codename,
    'codename': codename,
    'version': version,
    }

  stanzas.append(stanza.strip())


print "\n\n".join(stanzas)
