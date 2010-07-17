import cloudera.packaging.packaging
import cloudera.utils
import subprocess


class YumPackage(cloudera.packaging.packaging.Package):

    def __init__(self, name):
      self.pkg_name = name

    def name(self):
      return self.pkg_name

    def files(self):
      return cloudera.utils.simple_exec(["rpm", "-ql", self.pkg_name]).split("\n")


class YumPackageManager(cloudera.packaging.packaging.PackageManager):

    def __init__(self):
      pass


    def search_from_name(self, package_name):

      listing = cloudera.utils.simple_exec(["yum", "-d", "0", "search", package_name]).split("\n")[1:]
      packages = []
      for line in listing:
        name_arch_desc = line.split(" : ")
        name_arch = name_arch_desc[0]

        if name_arch and name_arch[0] != " ":
          (name, sep ,arch) = name_arch.partition('.')

          if name == package_name:
            package = YumPackage(name)
            package.pkg_arch = arch
            packages.append(package)

      return packages


    def install(self, packages):

      for package in packages:
        print cloudera.utils.simple_exec(["yum", "-y", "install", package.name()])


    def uninstall(self, packages):

      command = ["yum", "-y", "remove"]

      pkg_names = [package.name() for package in packages]
      command.extend(pkg_names)

      print cloudera.utils.simple_exec(command)


    def is_package_installed(self, pkg_name):

      found = [pkg for pkg in cloudera.utils.simple_exec(["rpm", "-qa", "--queryformat", '%{RPMTAG_NAME}\n']).split('\n') if pkg == pkg_name]

      if len(found) == 0:
        return False
      else:
        return True


