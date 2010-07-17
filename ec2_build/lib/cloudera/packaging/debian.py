import cloudera.packaging.packaging
import apt


class AptPackage(cloudera.packaging.packaging.Package):
  '''
  Apt package
  '''

  def __init__(self, apt_package):
    self.package = apt_package


  def files(self):
    return self.package.installed_files


  def name(self):
    return self.package.name


  def version(self):
    return self.package.installedVersion


class AptPackageManager(cloudera.packaging.packaging.PackageManager):
    '''
    Package manager for Apt platform
    '''

    def __init__(self):
      self.cache = apt.cache.Cache()


    def search_from_name(self, package_name):
      if self.cache.has_key(package_name):
        return [AptPackage(self.cache[package_name])]
      else:
        return []


    def install(self, packages):

      for package in packages:
        package.package.mark_install()

      self.cache.commit(apt.progress.TextFetchProgress(), apt.progress.InstallProgress())


    def uninstall(self, packages):

      for package in packages:
        package.package.mark_delete()

      self.cache.commit(apt.progress.TextFetchProgress(), apt.progress.InstallProgress())


    def is_package_installed(self, pkg_name):

      pkg = self.search_from_name(pkg_name)

      if len(pkg) == 0:
        return pkg[0].is_installed
      else:
        return False

