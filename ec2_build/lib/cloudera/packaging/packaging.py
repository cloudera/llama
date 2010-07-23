# Copyright (c) 2010 Cloudera, inc.
import platform


class PackageManagerFactory:
    '''
    Class in charge of instanciating the right PackageManager
    '''

    def __init__(self):
      pass


    def create_apt_package_manager(self):
      '''
      Create a package manager for Apt platforms
      '''

      import cloudera.packaging.debian
      return cloudera.packaging.debian.AptPackageManager()


    def create_yum_package_manager(self):
      '''
      Create a package manager for Yum platforms
      '''

      import cloudera.packaging.redhat
      return cloudera.packaging.redhat.YumPackageManager()


    def create_redhat_cmdline_package_manager(self):
      '''
      Create a package manager for Yum platforms using command line.
      This is used as a last resort when no python modules are available or in
      a good enough shape to be used
      '''

      import cloudera.packaging.redhat_cmdline
      return cloudera.packaging.redhat_cmdline.YumPackageManager()


    def create_debian_cmdline_package_manager(self):
      '''
      Create a package manager for Yum platforms using command line.
      This is used as a last resort when no python modules are available or in
      a good enough shape to be used
      '''

      import cloudera.packaging.debian_cmdline
      return cloudera.packaging.debian_cmdline.AptPackageManager()


    def create_package_manager(self):
      '''
      Instianciate a package manager for the current platform
      '''

      (os, release_version, release_name) = platform.dist()
      os = os.lower()
      release_name = release_name.lower()

      factory_for_release = {'lucid': self.create_apt_package_manager,
                            }

      factory_for_os = {'fedora': self.create_yum_package_manager,
                        'redhat': self.create_redhat_cmdline_package_manager, # Yum python binding is not stable
                        'ubuntu': self.create_debian_cmdline_package_manager, # Apt python binding is not stable
                        'debian': self.create_debian_cmdline_package_manager  # Apt python binding is not stable
                        }

      if release_name in factory_for_release:
        return factory_for_release[release_name]()
      elif os in factory_for_os:
        return factory_for_os[os]()
      else:
        raise Exception( os + " platform is not supported")

