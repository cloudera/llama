import platform

class Package:
  '''
  Interface for a package
  '''

  def __init__(self):
    pass

  def files(self):
    '''
    Return the list of files installed
    '''
    raise Exception("Not implemented")


  def name(self):
    '''
    Return package name
    @return Package name
    '''
    raise Exception("Not implemented")


  def is_installed(self):
    '''
    Return Boolean whether this package is installed or not
    @return Boolean whether this package is installed or not
    '''
    raise Exception("Not implemented")


  def version(self):
    '''
    Return Version
    @return Version
    '''
    raise Exception("Not implemented")






class PackageManager:
    '''
    Interface for a package manager
    '''

    def __init__(self):
      pass

    def search_from_name(self, package_name):
      '''
      @param package_name Package to look for
      @return List of Package
      '''
      raise Exception("Not implemented")


    def install(self, packages):
      '''
      @param packages List of packages
      '''
      raise Exception("Not implemented")


    def uninstall(self, packages):
      '''
      @param packages List of packages
      '''
      raise Exception("Not implemented")


    def is_package_installed(self, pkg_name):
      '''
      @param pkg_name Package name
      @return Boolean. True if installed. False otherwise (or zero ore more than one package found)
      '''
      raise Exception("Not implemented")



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

