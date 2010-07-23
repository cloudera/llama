# Copyright (c) 2010 Cloudera, inc.
import cloudera.packaging.packaging
import cloudera.utils
import subprocess


class YumPackage(cloudera.packaging.packaging.Package):
    '''
    See L{cloudera.packaging.debian.AptPackage} for documentation
    '''

    def __init__(self, name):
      self.pkg_name = name
      self.preferred_arch = None


    def name(self):
      return self.pkg_name


    def files(self):
      return cloudera.utils.simple_exec(["rpm", "-ql", self.pkg_name]).split("\n")


    def current_architecture(self):
      (rc, output) = cloudera.utils.simple_exec2(["uname", "-m"])
      str = ''.join([line for line in output])
      return str.strip()


    def set_preferred_arch(self, arch):

      if arch == 'current':
        arch = self.current_architecture()

      self.preferred_arch = arch


class YumPackageManager(cloudera.packaging.packaging.PackageManager):
    '''
    See L{cloudera.packaging.debian.AptPackageManager} for documentation
    '''


    def __init__(self):
      pass


    def search_from_name(self, package_name):

      listing = cloudera.utils.simple_exec(["yum", "-d", "0", "search", package_name]).split("\n")[1:]
      packages = []
      for line in listing:
        name_arch_desc = line.split(" : ")
        name_arch = name_arch_desc[0]

        if name_arch and name_arch[0] != " ":
          name_arch = name_arch.rsplit('.', 1)

          if len(name_arch) == 2:
            (name, arch) = name_arch

            if name == package_name:
              package = YumPackage(name)
              package.pkg_arch = arch
              packages.append(package)


      return packages


    def install(self, packages):

      for package in packages:

        package_name = package.name()
        if package.preferred_arch:
          package_name = package_name + "." + package.preferred_arch
        print cloudera.utils.simple_exec(["yum", "-y", "install", package_name])


    def uninstall(self, packages):

      if len(packages) > 0:

        command = ["yum", "-y", "erase"]

        pkg_names = [package.name() for package in packages]
        command.extend(pkg_names)

        print cloudera.utils.simple_exec(command)


    def is_package_installed(self, pkg_name):

      found = [pkg for pkg in cloudera.utils.simple_exec(["rpm", "-qa", "--queryformat", '"%{RPMTAG_NAME}\n"']).split('\n') if pkg == pkg_name]

      if len(found) == 0:
        return False
      else:
        return True


