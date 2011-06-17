# Copyright (c) 2010 Cloudera, inc.

import boto
import cloudera.aws.ec2
import cloudera.staging.Archive
import cloudera.staging.ArchiveManager
import cloudera.staging.ElasticIpManager
import cloudera.staging.StageManager
import cloudera.utils
import getpass
import os, re
import paramiko
import subprocess
import sys
import time
import tarfile

from cloudera.constants import RepositoryType
from cloudera.utils import display_message, verbose_print
from optparse import OptionParser
from ec2_constants import INTERIM_ARCHIVE_HOST

class ErrorEncountered(Exception):
  ''' A null exception wrapper '''
  pass


# s3cmd configuration file template
S3TEMPLATE = """[default]
access_key = %(access_key)s
acl_public = False
bucket_location = US
cloudfront_host = cloudfront.amazonaws.com
cloudfront_resource = /2008-06-30/distribution
default_mime_type = binary/octet-stream
delete_removed = False
dry_run = False
encoding = UTF-8
encrypt = False
force = False
get_continue = False
gpg_command = /usr/bin/gpg
gpg_decrypt = %%(gpg_command)s -d --verbose --no-use-agent --batch --yes --passphrase-fd %%(passphrase_fd)s -o %%(output_file)s %%(input_file)s
gpg_encrypt = %%(gpg_command)s -c --verbose --no-use-agent --batch --yes --passphrase-fd %%(passphrase_fd)s -o %%(output_file)s %%(input_file)s
gpg_passphrase =
guess_mime_type = True
host_base = s3.amazonaws.com
host_bucket = %%(bucket)s.s3.amazonaws.com
human_readable_sizes = False
list_md5 = False
preserve_attrs = True
progress_meter = True
proxy_host =
proxy_port = 0
recursive = False
recv_chunk = 4096
secret_key = %(secret_key)s
send_chunk = 4096
simpledb_host = sdb.amazonaws.com
skip_existing = False
use_https = True
verbosity = WARNING"""


# To be renamed ArchiveController ?
class Archive:

  # Base directory where all the action is going to be
  BASE_DIR = '/tmp'

  # Username to use for log in. ubuntu ami only allow ubuntu user
  USERNAME = 'ubuntu'

  GPG_ENV_VARIABLE_APT = "GNUPGHOME=" + BASE_DIR + "/apt/gpg-home/"
  GPG_ENV_VARIABLE_YUM = "GNUPGHOME=" + BASE_DIR + "/yum/gnupg/"

  # Since we
  SSH_NO_STRICT_HOST_KEY_CHECKING_OPTION = '-o StrictHostKeyChecking=no'

  # Packages to be installed on the instance before proceeding to the staging
  PACKAGES_TO_INSTALL = ['gnupg-agent', 'gnupg2', 's3cmd']


  def __init__(self):

    # List of environment variables to use
    self.env = []
    self.gpg_env_home = []
    self.gpg_env = []
    self.passphrase = {}

    self.username= Archive.USERNAME

  def get_env(self):
    return ' '.join(self.env + self.gpg_env_home + self.gpg_env)


  def connect(self, hostname, key_file, username = USERNAME):
    """
    Establish a connection with the archive

    @param hostname Archive hostname
    @param key_file SSH private key filename
    @param username Username to use for login
    """

    self.username = username

    self.ssh = paramiko.SSHClient()
    self.ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    key = paramiko.RSAKey.from_private_key_file(key_file)
    print "CONNECTING: host: %s; user: %s; key: %s"%(hostname, username, key_file)
    self.ssh.connect(hostname=hostname, username=username, pkey=key)

    self.ssh.get_transport().set_keepalive(120)

  def execute(self, cmd, redirect_stdout_to_stderr=False, prepend_env=True):
    """
    Execute a remote command and print stdout and stderr to screen

    @param cmd Command to be executed
    @redirect_stdout_to_stderr Redirect stderr to stdout
    """

    executed_cmd = cmd

    if prepend_env:
      executed_cmd = self.get_env() + " " + executed_cmd

    if redirect_stdout_to_stderr:
      executed_cmd = executed_cmd + ' 2>&1'

    print "EXECUTING: [%s]"%(executed_cmd)
    stdin, stdout, stderr = self.ssh.exec_command(executed_cmd)
    stdin.close()

    # XXX Needs to find a better way to multiplex stdout and stderr other than redirection
    for line in stdout:
      print line.strip()

    for line in stderr:
      print line.strip()

    stdout.close()
    stderr.close()


  def copy_scripts(self, host, key_file, staging, build_id, interim_staging):
    """
    Copy scripts to update deb and yum repositories

    @param host destination hostname
    @param key_file SSH private key filename
    """

    display_message("Cleanup script area:")
    self.execute("rm -rf " + Archive.BASE_DIR + "/apt")
    self.execute("rm -rf " + Archive.BASE_DIR + "/yum")
    self.execute("rm -rf " + Archive.BASE_DIR + "/ec2_build")

    display_message("Copy apt related scripts:")
    subprocess.call(["scp", Archive.SSH_NO_STRICT_HOST_KEY_CHECKING_OPTION, '-i', key_file, '-r', '../../apt', self.username + '@' + host + ':' + Archive.BASE_DIR + '/apt'])

    display_message("Copy yum related scripts:")
    subprocess.call(["scp", Archive.SSH_NO_STRICT_HOST_KEY_CHECKING_OPTION, '-i', key_file, '-r', '../../yum', self.username + '@' + host + ':' + Archive.BASE_DIR + '/yum'])

    display_message("Copy ec2_build related scripts:")
    subprocess.call(["scp", Archive.SSH_NO_STRICT_HOST_KEY_CHECKING_OPTION, '-i', key_file, '-r', '../../ec2_build', self.username + '@' + host + ':' + Archive.BASE_DIR + '/ec2_build'])

    display_message("Copy source to %s:" % (INTERIM_ARCHIVE_HOST, ))
    p = subprocess.Popen(["scp", Archive.SSH_NO_STRICT_HOST_KEY_CHECKING_OPTION, '-i', '../../ec2_build/conf/static_vm_key', '-r', staging, 'root@' + INTERIM_ARCHIVE_HOST + ':' + interim_staging + '/'])

    upload_returncode = p.wait()

    if upload_returncode != 0:
      raise ErrorEncountered("Could not upload build to interim")

    display_message("Copy bits (now to nightly):")
    ssh_cmd = "scp %s -i /root/.ssh/nightly_build.pem -r %s %s@%s:%s/%s" % (Archive.SSH_NO_STRICT_HOST_KEY_CHECKING_OPTION,  interim_staging + '/' + build_id, self.username,
                                                                      host, Archive.BASE_DIR, build_id)

    display_message("ssh cmd: %s" % (ssh_cmd, ))

    temp_ssh = open("/tmp/ssh_cmd", "w")
    temp_ssh.write(ssh_cmd)
    temp_ssh.close()
    subprocess.call(["chmod", "+x", "/tmp/ssh_cmd"]);
    subprocess.call(["scp", Archive.SSH_NO_STRICT_HOST_KEY_CHECKING_OPTION, '-i', '../../ec2_build/conf/static_vm_key', '/tmp/ssh_cmd', 'root@' + INTERIM_ARCHIVE_HOST + ':/tmp/ssh_cmd'])
    
    p2 = subprocess.Popen(["ssh", Archive.SSH_NO_STRICT_HOST_KEY_CHECKING_OPTION, '-i', '../../ec2_build/conf/static_vm_key', 'root@' + INTERIM_ARCHIVE_HOST, '/tmp/ssh_cmd'])

    upload2_returncode = p2.wait()

    if upload2_returncode != 0:
      raise ErrorEncountered("Could not upload build to real nightly")

    self.execute("sudo chown -R www-data " + Archive.BASE_DIR + "/" + build_id)
    
  def install_packages(self):
    """
    Update instance and install extra packages needed for the archive deployment
    """

    # Update and install extra packages
    display_message("Update system:")
    self.execute("sudo apt-get update", True)
    self.execute("sudo apt-get -y upgrade", True)

    display_message("Install " + ", ".join(Archive.PACKAGES_TO_INSTALL))
    self.execute("sudo apt-get -y install " + ' '.join(Archive.PACKAGES_TO_INSTALL), True)

    # Create s3cmd config file
    display_message("Setup s3cmd configuration file")
    s3_config_content = S3TEMPLATE % {'access_key': os.getenv('AWS_ACCESS_KEY_ID'),
                         'secret_key': os.getenv('AWS_SECRET_ACCESS_KEY')}
    self.execute('echo "' + s3_config_content + '" > /home/' + self.username + '/.s3cfg')

    # Also, while we're here, set up /var/www/archive_public to actually be a symlink to /mnt/archive_public.
    self.execute("sudo mkdir -p /mnt/archive_public", True)
    self.execute("sudo cp -r /var/www/archive_public/* /mnt/archive_public/", True)
    self.execute("sudo chown -R www-data:www-data /mnt/archive_public", True)
    self.execute("sudo rm -rf /var/www/archive_public", True)
    self.execute("sudo ln -s /mnt/archive_public /var/www/archive_public", True)
    self.execute("sudo chown -R www-data:www-data /var/www/archive_public", True)


  def get_gpg_info_path(self, system):
    if system == RepositoryType.APT:
      return Archive.BASE_DIR + '/.gpg-agent-info.apt'
    elif system == RepositoryType.YUM:
      return Archive.BASE_DIR + '/.gpg-agent-info.yum'
    else:
      raise Exception("Unknown system: %s"(str(system)))

  def get_gpg_env(self, system):
    """
    Retrieve gpg environment variable used for contacting gpg-agent
    and adds it to the global list of environment variables
    """

    display_message("Retrieve GPG environment variable")
    stdin, stdout, stderr = self.ssh.exec_command('cat ' + self.get_gpg_info_path(system))
    lines = [line.strip() for line in stdout]

    self.gpg_env = lines

    # Sets gpg home dir
    if system == RepositoryType.APT:
      self.gpg_env_home = [self.GPG_ENV_VARIABLE_APT]
    elif system == RepositoryType.YUM:
      self.gpg_env_home = [self.GPG_ENV_VARIABLE_YUM]


  def setup_gpg(self):
    """
    Stetup gpg directories
    """

    # user www-data needs access to our gpg home
    for directory in [Archive.BASE_DIR + '/apt/gpg-home/', Archive.BASE_DIR + '/yum/gnupg']:
      display_message("Setup gpg home directory for " + directory)
      self.execute('chmod -R 777 ' + directory)


  def start_gpg(self, system):
    """
    Start gpg-agent
    """

    # Start gpg-agent
    # XXX Do not redirect stdout to stderr when starting gpg-agent.
    # Some weird issues would block the connection
    # Seems to be related to tty
    display_message("Start gpg-agent")
    self.execute('sudo -E -u www-data gpg-agent --daemon  --write-env-file "' + self.get_gpg_info_path(system) + '" --homedir ' + Archive.BASE_DIR + '/apt/gpg-home/ --allow-preset-passphrase', prepend_env=False)


  def set_gpg_passphrase(self, fingerprint, passphrase):
    """
    Set GPG passphrase

    @param fingerprint gpg key fingerprint
    @param passphrase Passphrase
    """

    self.passphrase[fingerprint] = passphrase

    display_message("Set gpg passphrase")
    stdin, stdout, stderr = self.ssh.exec_command( ' sudo -E -u www-data ' + self.get_env() + " " + '/usr/lib/gnupg2/gpg-preset-passphrase -v  --preset ' + fingerprint)
    stdin.write(passphrase)
    stdin.close()
    lines = [line for line in stdout]
    stderr_lines = [line for line in stderr]
    line =  "".join(lines + stderr_lines)
    print line


  def update_deb_repo(self, build, cdh_release, freezer_bucket):
    """
    Start script to update debian repository

    @param build Build to be published
    """

    #display_message("Clean up previous builds")
    #self.execute(' sudo rm -rf ' + Archive.BASE_DIR + '/' + build, True)

    display_message("Update deb repository")
    self.execute(' sudo -E -u www-data ' + Archive.BASE_DIR + '/apt/update_repo.sh -s ' + freezer_bucket + ' -b ' + build + ' -c cdh' + cdh_release + ' -r /var/www/archive_public/debian/', True)


  def update_yum_repo(self, build, cdh_version):
    """
    Start script to update red hat repository

    @param build Build to be published
    """

    # Update .rpmmacros with maintenair gpg key name
    self.execute(' sudo -E -u www-data ' + ' echo "%_gpg_name              ' + cloudera.constants.YUM_MAINTENER_GPG_KEY + '" >> ~/.rpmmacros', True)

    # Update repositories
    display_message("Update yum repository")
    self.execute(' sudo -E -u www-data ' + Archive.BASE_DIR + '/yum/update_repos.sh -s ' + Archive.BASE_DIR + '/' + build + '/ -c ' + cdh_version + ' -r /var/www/archive_public/redhat/' + ' -p' + self.passphrase[cloudera.constants.GPG_KEY_FINGERPRINT_FOR_REPOSITORY_KIND[RepositoryType.YUM]], True)
    self.execute(' sudo -E -u www-data ' + Archive.BASE_DIR + '/yum/update_repos.sh -s ' + Archive.BASE_DIR + '/' + build + '/ -c ' + cdh_version + ' -d rhel6 -a x86_64 -r /var/www/archive_public/redhat/6/x86_64/' + ' -p' + self.passphrase[cloudera.constants.GPG_KEY_FINGERPRINT_FOR_REPOSITORY_KIND[RepositoryType.YUM]], True)
    self.execute(' sudo -E -u www-data ' + Archive.BASE_DIR + '/yum/update_repos.sh -s ' + Archive.BASE_DIR + '/' + build + '/ -c ' + cdh_version + ' -d sles11 -a x86_64 -r /var/www/archive_public/sles/11/x86_64/' + ' -p' + self.passphrase[cloudera.constants.GPG_KEY_FINGERPRINT_FOR_REPOSITORY_KIND[RepositoryType.YUM]], True)


  def finalize_staging(self, build, cdh_version):
    """
    Start program to finalize staging.
    It means copying the tarball, its changelog and docs

    @param build Build to be published
    """

    display_message("Finalize staging")
    self.execute(' sudo -E -u www-data ' + Archive.BASE_DIR + '/ec2_build/bin/finalize-staging.sh -b '+ build + ' -c ' + cdh_version + ' -r /var/www/archive_public/', True)

  def clean_up(self):
    """
    Clean up left over files such as our gpg keys.
    """

    display_message("Remove gpg keys")
    for gpg_homedir in ['/apt/gpg-home', '/yum/gnupg']:
      self.execute(' sudo rm -rf ' + Archive.BASE_DIR + gpg_homedir)

