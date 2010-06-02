# dict from (distro, arch) => AMI ID
# These AMIs must run their userdata on startup if it has
# a shebang!
AMIS={
# Alestic ubuntu/debian AMIs
  ('lucid', 'x86'):      'ami-2d4aa444',
  ('lucid', 'amd64'):    'ami-fd4aa494',
  ('karmic', 'x86'):     'ami-1515f67c',
  ('karmic', 'amd64'):   'ami-ab15f6c2',
  ('jaunty', 'x86'):     'ami-0d729464',
  ('jaunty', 'amd64'):   'ami-1f749276',
  ('intrepid', 'x86'):   'ami-0372946a',
  ('intrepid', 'amd64'): 'ami-1374927a',
  ('hardy', 'x86'):      'ami-0772946e',
  ('hardy', 'amd64'):    'ami-1774927e',
  ('lenny', 'x86'):      'ami-1d729474',
  ('lenny', 'amd64'):    'ami-ed749284',
# home built centos5 AMIs
  ('centos5', 'x86'):    'ami-41e30328',
  ('centos5', 'amd64'):  'ami-5fe30336',
  }

# What kind of instances should be started to run the various builds
BUILD_INSTANCE_TYPES = {
  'x86':   'm1.small',
  'amd64': 'm1.large',
  }

# What we actually want to build
# tuples of (build type, distro, arch)
DEFAULT_BUILD_MACHINES = [
  ('deb', 'lucid', 'x86'),
  ('deb', 'lucid', 'amd64'),
  ('deb', 'karmic', 'x86'),
  ('deb', 'karmic', 'amd64'),
  ('deb', 'jaunty', 'x86'),
  ('deb', 'jaunty', 'amd64'),
  ('deb', 'hardy',    'x86'),
  ('deb', 'hardy',    'amd64'),
  ('deb', 'lenny',    'x86'),
  ('deb', 'lenny',    'amd64'),
  ('rpm', 'centos5',  'x86'),
  ('rpm', 'centos5',  'amd64'),
  ]
