# Copyright (c) 2010 Cloudera, inc.

from cloudera.utils import verbose_print
import datetime
import time
import boto.exception

# Time to wait after requesting the EIP association for being effective
EIP_ASSOCIATION_TIME = 4*60 # in seconds


# An EC@ instance can be in different states
class InstanceState:
  PENDING = 'pending'
  RUNNING = 'running'
  SHUTTING_DOWN = 'shutting-down'
  TERMINATED = 'terminated'
  STOPPING   = 'stopping'
  STOPPED    = 'stopped'


def wait_for_eip_association_ready():
  '''
  Until we find a better way to check if an eip is effectively associated
  we will just sleep for EIP_ASSOCIATION_TIME
  '''
  time.sleep(EIP_ASSOCIATION_TIME)


def create_volume_from_snapshot(ec2_connection, snap_id, availability_zone, size=None):
  '''
  This creates a new volume based on a snapshot id

  @param ec2_connection EC2 connection object
  @param snap_id Snapshot id to create the volume from
  @param availability_zone Availability zone for the volume
  @param size Size of the new volume in GB, If None, its size will be equal to the snapshot's one
  @return Newly create volume
  '''

  verbose_print("Creating volume with snap_id: %s" % snap_id)
  return(ec2_connection.create_volume(size, availability_zone, snapshot=snap_id))


def wait_while_booting(instance, wait_interval=5, logger=None):
  '''
  This function blocks while a given instance is booting

  @param instance Intance to wait on
  @param wait_interval How long to wait between each check
  '''

  print_fun = lambda line: verbose_print(line)
  if logger:
    print_fun = lambda line: logger.info(line)


  state = instance.state
  print_fun("Instance state: %s" %(state))

  while state == InstanceState.PENDING:
    time.sleep(wait_interval)
    instance.update()
    state = instance.state
    print_fun("Waiting for %s seconds ... current state: %s"%(str(wait_interval), state))

  if state != InstanceState.RUNNING:
    raise Exception("Couldn't boot instance %s"%(instance.id))

def swap_associated_elastic_ips(ec2_connection, ip1, ip2):
  '''
  Swap the ip addresses between two instances

  @param ec2_connection EC2 Connection object
  @param ip1 First Elastic IP (String)
  @param ip2 Second Elastic IP (String)
  '''

  eip1 = ec2_connection.get_all_addresses([ip1])[0]
  eip2 = ec2_connection.get_all_addresses([ip2])[0]


  instance_id1 = eip1.instance_id
  instance_id2 = eip2.instance_id

  # Dissociate addresses from their instances
  ec2_connection.disassociate_address(eip1.public_ip)
  ec2_connection.disassociate_address(eip2.public_ip)
  wait_for_eip_association_ready()

  # Re-associate them
  ec2_connection.associate_address(instance_id1, eip2.public_ip)
  ec2_connection.associate_address(instance_id2, eip1.public_ip)
  wait_for_eip_association_ready()


def data_volume_for_instance(instance, volumes_names):
  '''
  Return a list of volumes mounted on an instance from their mounting point

  @param instance Amazon EC2 Instance
  @param volumes_names List of mounting points
  @return List of volumes
  '''

  volumes_mapping = instance.block_device_mapping

  volumes = []
  for mapped_volume in volumes_mapping:
    if mapped_volume in volumes_names:
      volumes.append(volumes_mapping[mapped_volume])

  return volumes


def throw_away_volume(ec2_connection, volume):
  '''
  Delete a volume

  @param ec2_connection Amazon EC2 Connection
  @param volume Volume to be deleted
  '''

# Maybe None
  if not volume:
    return

  ec2_connection.delete_volume(volume.volume_id)


def instance_for_instance_id(ec2_connection, instance_id):
  '''
  Return an instance from an instance id

  @param ec2_connection Amazon EC2 Connection
  @param instance_id Instance id
  '''

  reservations = ec2_connection.get_all_instances([instance_id])

  instance = None
  if len(reservations) > 0:
    reservation = reservations[0]
    if len(reservation.instances) > 0:
      instance = reservation.instances[0]

  return instance


def cleanup_security_group(ec2_connection, security_group_name):
  '''
  Remove security group if unused

  @param ec2_connection Connection to EC2
  @param security_group_name Security group name
  '''

  if not security_group_name:
    return

  verbose_print("Deleting security group [%s]"%(security_group_name))
  ec2_connection.delete_security_group(security_group_name)


def launch_time_datetime(instance):

  iso_format = "%Y-%m-%dT%H:%M:%S.%fZ"
  return datetime.datetime.strptime(instance.launch_time, iso_format)


# What user shall be used to remote login in any instance
def user_for_os(os):
  user_for_os = {
                  'lucid': 'ubuntu',
                  'karmic': 'ubuntu',
                }

  if os in user_for_os:
    return user_for_os[os]
  else:
    return 'root'


# What distributions don't have sudo
def distributions_without_sudo():
  return ['centos5', 'lenny']
