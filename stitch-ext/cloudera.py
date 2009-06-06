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


def DebTarget(package_name,
              deb_source_name=None):

  deb_source_name = deb_source_name or package_name

  pkgdir = "%%(assemblydir)/%s-${%s.base.version}" % (deb_source_name, package_name)

  return PackageTarget(
    package_name = "%s-deb" % package_name,
    create_tarball=False,
    clean_first=True,
    steps = [
      MakeDir(dirname=pkgdir),
      SetupBuildStep(package_name, pkgdir),
      CopyFile(
        src_file = "${%s.pristine.tarball}" % package_name,
        dest_file = "%s_${%s.base.version}.orig.tar.gz" % (deb_source_name, package_name)),
      CopyDir(
        src_dir = "deb/debian.%s/" % package_name,
        exclude_patterns=['*.ex', '*.EX', '.*~'],
        dest_dir = "%s/debian" % pkgdir),
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
  rpm_release = "${%s.rpm.release}" % package_name
  cloudera_ver_string = "%s-%s-%s" % (package_name, base_ver, rpm_release)

  return PackageTarget(
    package_name = "%s-srpm" % package_name,
    clean_first = True,
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
        executable="//tools/create-cloudera-dir",
        arguments=["${%s.repo}" % package_name,
                   "${%s.base.ref}" % package_name,
                   "${%s.build.ref}" % package_name,
                   "$/cloudera"],
        inputs=["${%s.repo}/" % package_name]),
      Exec(
        executable = "${tar-exec}",
        arguments = ["-czf",
                     "%%(assemblydir)/topdir/SOURCES/cloudera-%s.tar.gz" % rpm_release,
                     "cloudera"],
        dir = "%(assemblydir)"),
      Exec(
        executable="%(srcdir)/rpm/create_rpms",
        arguments=[
          package_name,
          "%(assemblydir)/",
          "%(assemblydir)/topdir",
          rpm_release,
          base_ver]),
      CopyDir(
        src_dir ="$/topdir/SRPMS/",
        dest_dir="${rpmdir}/SRPMS/"),
    ],
    outputs = [("$/topdir/SRPMS/%s.src.rpm" % cloudera_ver_string),
             ("${rpmdir}/SRPMS/%s.src.rpm" % cloudera_ver_string)])
