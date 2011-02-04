# Copyright (c) 2010 Cloudera, inc.

import cloudera.constants
import cloudera.utils
import cloudera.aws.ec2
import cloudera.staging.StageManager
import time

from cloudera.utils import display_message, verbose_print
from cloudera.constants import OFFICIAL_ARCHIVE_IP_ADDRESS, NIGHTLY_ARCHIVE_IP_ADDRESS

class ArchiveManager:

  DEFAULT_AVAILABILITY_ZONE = 'us-east-1a'
  DEFAULT_ARCHIVE_VOLUME_MOUNT = '/dev/sdb'
  DEFAULT_AMI = 'ami-3397795a'

  NIGHTLY_AMI = 'ami-ee03f387'


  def __init__(self, ec2_connection):
    self.ec2_connection = ec2_connection
    self.EC2_IPS_GROUP = ec2_connection.get_all_security_groups(['EC2-IPS'])[0]


  def launch_server(self, security_groups, volume, key_name, ami = DEFAULT_AMI):
    '''
    This launches a new instance attaches a volume and security group to it

    @param security_group Security group
    @param volume Volume to be mounted
    @param key_name NAme of the key to be associated
    @param ami AMI to use
    @return Newly started instance
    '''
    # Could be None
    if not ami:
      ami = ArchiveManager.DEFAULT_AMI

    groups = [self.EC2_IPS_GROUP]
    image = self.ec2_connection.get_all_images(image_ids=ami)[0]
    verbose_print("Launching server with image: %s" %image)
    reservation = image.run(key_name = key_name, security_groups = groups.extend(security_groups), placement=self.DEFAULT_AVAILABILITY_ZONE)
    instance = reservation.instances[0]
    cloudera.aws.ec2.wait_while_booting(instance)

    if ami == ArchiveManager.DEFAULT_AMI:
      msg = "Attaching vol: %s to mount: %s" %(volume, ArchiveManager.DEFAULT_ARCHIVE_VOLUME_MOUNT)
      verbose_print(msg)
      volume.attach(instance.id, ArchiveManager.DEFAULT_ARCHIVE_VOLUME_MOUNT)

    return instance


  def create_security_group(self, security_group_name, security_group_desc):
    '''
    This creates a template security group for new staging instances

    @oaram security_group_name Security group name
    @param security_group_desc Security group description
    @return Newly created security group
    '''

    verbose_print("Creating Security Group %s " % security_group_name)
    sec_group = self.ec2_connection.create_security_group(security_group_name, security_group_desc)
    #TODO make this more restrictive/accurate It might make sense to make it a config file.
    sec_group.authorize('tcp', 80, 80, cloudera.constants.CLOUDERA_NETWORK)
    sec_group.authorize('tcp', 22, 22, '0.0.0.0/0')
    return sec_group


  def kickOff(self, snapshot, security_group_name, security_group_desc, key_name, ami):
    '''
    Start a new instance

    @param snapshot Snapshot to base on the volume to be created
    @param security_group Security group name
    @param security_group_desc Security group description
    @param key_name Key name
    @param ami Ami to base the instance on
    @return Newly launched instance
    '''

    security_group = self.create_security_group(security_group_name, security_group_desc)
    volume = cloudera.aws.ec2.create_volume_from_snapshot(self.ec2_connection, snapshot, ArchiveManager.DEFAULT_AVAILABILITY_ZONE)
    instance = self.launch_server([security_group], volume, key_name, ami)
    return instance


  def promote(self, instance, eip):
    '''
    Promote an already staged archive to be the official one (archive.cloudera.com)

    @param instance Instance to promote
    @param eip Current elastic IP used by the instance to be promoted
    '''

    official_archives_addresses = self.ec2_connection.get_all_addresses([OFFICIAL_ARCHIVE_IP_ADDRESS])

    if len(official_archives_addresses) > 0:

      official_archive_address = official_archives_addresses[0]
      stageManager = cloudera.staging.StageManager.StageManager()

      if official_archive_address.instance_id:

        # Will need to untag those marked as previously official
        previous_official_archives = stageManager.get_all_instances([(stageManager.ATTRIBUTE_STATUS, stageManager.STATUS_PREVIOUSLY_OFFICIAL)])

        # We need to keep track of the previous official archive in case we have to roll back
        stageManager.tag_instance(official_archive_address.instance_id, stageManager.ATTRIBUTE_STATUS, stageManager.STATUS_PREVIOUSLY_OFFICIAL)

        # Case 1: There is already an official archive. Needs to swap addresses and update stage nanager
        verbose_print("Official archive already there. Associating instance %s to ip address %s"%(instance.id, OFFICIAL_ARCHIVE_IP_ADDRESS))
        cloudera.aws.ec2.swap_associated_elastic_ips(self.ec2_connection, eip, OFFICIAL_ARCHIVE_IP_ADDRESS)

        # Now we have a new "previous official archive" we can remove the others (normally "There can be only one")
        for instance_info in previous_official_archives:
          stageManager.tag_instance(instance_info[stageManager.ATTRIBUTE_INSTANCE_ID], stageManager.ATTRIBUTE_STATUS, stageManager.STATUS_UNOFFICIAL)


      else:

        # Case 2: There is no official archive
        verbose_print("No official archive on. Associating instance %s to ip address %s"%(instance.id, OFFICIAL_ARCHIVE_IP_ADDRESS))
        self.ec2_connection.disassociate_address(eip)
        self.ec2_connection.associate_address(instance.id, OFFICIAL_ARCHIVE_IP_ADDRESS)

      stageManager.tag_instance(instance.id, cloudera.staging.StageManager.StageManager.ATTRIBUTE_STATUS, cloudera.staging.StageManager.StageManager.STATUS_OFFICIAL)

    else:
      verbose_print("Couldn't find any record of the official archive ip address")


  def tear_down_archive(self, instance):
    stageManager = cloudera.staging.StageManager.StageManager()
    volumes = cloudera.aws.ec2.data_volume_for_instance(instance, [cloudera.staging.ArchiveManager.ArchiveManager.DEFAULT_ARCHIVE_VOLUME_MOUNT])
    instance_info = stageManager.get_instance(instance.id)

    display_message("Terminating instance %s"%(instance.id))
    self.ec2_connection.terminate_instances([instance.id])
    while(instance.update() != 'terminated'):
      print "Waiting for the instance to terminate. Current state is [%s]"%(instance.state)
      time.sleep(5)

    if volumes:
      volume = volumes[0]
      if not volume:
        print "Couldn't find volume"
      else:
        display_message("Deleting volume %s"%(volume.volume_id))
        cloudera.aws.ec2.throw_away_volume(self.ec2_connection, volume)
    else:
      print "No volume found"


    cloudera.aws.ec2.cleanup_security_group(self.ec2_connection, instance_info[cloudera.staging.StageManager.StageManager.ATTRIBUTE_SECURITY_GROUP])

    stageManager.delete_instance(instance.id)


  def promote_nightly(self, instance, eip):
    '''
    Promote an already staged archive to be the official one (archive.cloudera.com)

    @param instance Instance to promote
    @param eip Current elastic IP used by the instance to be promoted
    '''

    nightly_archives_addresses = self.ec2_connection.get_all_addresses([NIGHTLY_ARCHIVE_IP_ADDRESS])
    replaced_archive = None

    if len(nightly_archives_addresses) > 0:

      nightly_archive_address = nightly_archives_addresses[0]
      stageManager = cloudera.staging.StageManager.StageManager()

      if nightly_archive_address.instance_id:
        current_nightly = nightly_archive_address.instance_id

        # Case 1: There is already a nightly archive. Needs to deassociate addresses and update stage nanager
        verbose_print("Official archive already there. Associating instance %s to ip address %s"%(instance.id, NIGHTLY_ARCHIVE_IP_ADDRESS))
        cloudera.aws.ec2.swap_associated_elastic_ips(self.ec2_connection, eip, NIGHTLY_ARCHIVE_IP_ADDRESS)

        replaced_archive = current_nightly

      else:

        # Case 2: There is no official archive
        verbose_print("No official archive on. Associating instance %s to ip address %s"%(instance.id, NIGHTLY_ARCHIVE_IP_ADDRESS))
        self.ec2_connection.associate_address(instance.id, NIGHTLY_ARCHIVE_IP_ADDRESS)

    else:
      verbose_print("Couldn't find any record of the nightly archive ip address")

    return replaced_archive
