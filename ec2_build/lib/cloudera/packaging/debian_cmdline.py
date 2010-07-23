# Copyright (c) 2010 Cloudera, inc.
import cloudera.packaging.packaging
import cloudera.utils
import subprocess


class AptPackage(cloudera.packaging.packaging.Package):
    '''
    See L{cloudera.packaging.debian.AptPackage} for documentation
    '''

    def __init__(self, name):
      self.pkg_name = name


    def name(self):
      return self.pkg_name


    def files(self):
      return cloudera.utils.simple_exec(["dpkg", "-L", self.pkg_name]).split("\n")


    def version(self):
      return cloudera.utils.simple_exec(["dpkg-query", "-W", "-f", "${Version}", pkg_name])


class AptPackageManager(cloudera.packaging.packaging.PackageManager):
    '''
    See L{cloudera.packaging.debian.AptPackageManager} for documentation
    '''

    DEBIAN_NON_INTERACTIVE_FRONTEND = "DEBIAN_FRONTEND=noninteractive"

    def __init__(self):
      pass


    def search_from_name(self, package_name):

      listing = cloudera.utils.simple_exec(["apt-cache", "search", package_name]).split("\n")
      packages = []
      for line in listing:
        name_desc = line.split(" - ", 1)

        if len(name_desc) > 1:
          (name, desc) = name_desc

          if name == package_name:
            package = AptPackage(name)
            packages.append(package)

      return packages


    def install(self, packages):

      for package in packages:
        print cloudera.utils.simple_exec(["env", self.DEBIAN_NON_INTERACTIVE_FRONTEND, "apt-get", "-y", "install", package.name()])


    def uninstall(self, packages):

      command = ["env", self.DEBIAN_NON_INTERACTIVE_FRONTEND, "apt-get", "-y", "remove"]

      pkg_names = [package.name() for package in packages]
      command.extend(pkg_names)

      print cloudera.utils.simple_exec(command)


    def is_package_installed(self, pkg_name):

      (rc, result) = cloudera.utils.simple_exec2(["dpkg-query", "-W", "-f", "'${Status}'", pkg_name])
      line = ''.join([output for output in result])

      if line == "install ok installed":
        return True
      elif line == "purge ok not-installed":
        return False
      else:
        return None
