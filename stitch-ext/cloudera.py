from stitch.targets.alltargets import PackageTarget
from stitch.steps.filesteps import *
from stitch.steps.execstep import *

def SetupBuildStep(package_name,
                           dst_dir="%(assemblydir)/"):
  return Exec(
    executable="//tools/setup-package-build",
    arguments=["${%s.repo}" % package_name,
               "${%s.base.ref}" % package_name,
               "${%s.build.ref}" % package_name,
               "${%s.pristine.tarball}" % package_name,
               dst_dir],
    inputs=["${%s.repo}/" % package_name])

def GenerateDebianChangesStep(package_name,
                              dst_file):
  """ Generate a Debian "changelog" file from the git repository. """
  return Exec(
    executable="//tools/generate-debian-changelog",
    arguments=["${%s.repo}" % package_name,
               "${%s.base.ref}" % package_name,
               "${%s.build.ref}" % package_name,
               package_name,
               dst_file],
    inputs=["${%s.repo}/" % package_name])

def DebTarget(package_name):
  pkgdir = "%%(assemblydir)/%s-${%s.base.version}" % (package_name, package_name)

  return PackageTarget(
    package_name = "%s-deb" % package_name,
    create_tarball=False,
    clean_first=True,
    steps = [
      MakeDir(dirname=pkgdir),
      SetupBuildStep(package_name, pkgdir),
      CopyFile(
        src_file = "${%s.pristine.tarball}" % package_name,
        dest_file = "%s_${%s.base.version}.orig.tar.gz" % (package_name, package_name)),
      CopyDir(
        src_dir = "deb/debian.%s/" % package_name,
        exclude_patterns=['*.ex', '*.EX', '.*~'],
        dest_dir = "%s/debian" % pkgdir),
      GenerateDebianChangesStep(package_name,
                                "%s/debian/changelog" % pkgdir),
      Exec(
        executable="${debuild-exec}",
        arguments=['-uc',
                   '-us',
                   '-sa',
                   '-S'],
        dir=pkgdir),
    ])

def RpmTarget(package_name):
  base_ver = "${%s.base.version}" % package_name
  return PackageTarget(
    package_name = "%s-srpm" % package_name,
    clean_first = True,
    create_tarball=False,
    steps = [
    # Should be able to use OutputComponent here but couldn't get it to work
      CopyDir(
        src_dir = "rpm/topdir",
        dest_dir = "$/topdir"),
      MakeDir(dirname="$/topdir/SOURCES"),
      Exec( # TODO using CopyFile here would be better, but it screws up
        executable="/bin/cp",
        arguments=["${%s.pristine.tarball}" % package_name,
                   "$/topdir/SOURCES/"]),
      Exec(
        executable="//tools/create-cloudera-tarball",
        arguments=["${%s.repo}" % package_name,
                   "${%s.base.ref}" % package_name,
                   "${%s.build.ref}" % package_name,
                   "$/topdir/SOURCES/"],
        inputs=["${%s.repo}/" % package_name]),    
      Exec(
        executable="%(srcdir)/rpm/create_rpms",
        arguments=[
          package_name,
          "%(assemblydir)/",
          "%(assemblydir)/topdir",
          "${%s.repo}" % package_name,
          base_ver]),
      CopyDir(
        src_dir ="$/topdir/SRPMS/",
        dest_dir="${rpmdir}/SRPMS/"),
    ])
