# Copyright (c) 2010 Cloudera, inc.
# https://wiki.cloudera.com/display/PRODUCT/Staging+step

import boto
import boto.sdb.connection


class StageManager:
  '''
  Class used for managing staging servers
  '''
  
  # Amazon SDB domain
  STAGE_MANAGER_DOMAIN = "StageManagerDomain"

  # Attributes used to store information about staging servers
  ATTRIBUTE_INSTANCE_ID = "instanceid"
  ATTRIBUTE_USER = "user"
  ATTRIBUTE_STATUS = "status"
  ATTRIBUTE_SECURITY_GROUP = "securitygroup"

   # Archive status
  STATUS_OFFICIAL = 'official'
  STATUS_UNOFFICIAL = 'unofficial'
  STATUS_PREVIOUSLY_OFFICIAL = 'previously-official'


  def __init__(self):
    """
    Initialize connections to Amazon services  
    """

    self.sdb_connection = boto.sdb.connection.SDBConnection()
    self.ec2_connection = boto.connect_ec2()


  def init_db(self):
    """
    Delete domain and recreate a blank one
	 Note: All data will be lost and unrecoverable. Be cautious
    """
      
    # First step: Delete data and domain if it exists
    if self.sdb_connection.lookup(StageManager.STAGE_MANAGER_DOMAIN):
      self.sdb_connection.delete_domain(StageManager.STAGE_MANAGER_DOMAIN)
  
    # Second step: Recreate domain
    self.sdb_connection.create_domain(StageManager.STAGE_MANAGER_DOMAIN)


  def add_instance(self, instance_id, status=STATUS_UNOFFICIAL, user=None, security_group=None):
    """
    Add a new instance to keep track of

    @param instance_id Instance id as a string
    @param status Status of the archive (ie. official, unofficial)
    @param user Name of the user
    @param security_group Security group associated with the instance
    @return Bool Whether the operation has succeeded or not
    """

    return self.sdb_connection.put_attributes(StageManager.STAGE_MANAGER_DOMAIN,
                                instance_id,
                                {
                                  StageManager.ATTRIBUTE_INSTANCE_ID: instance_id,
                                  StageManager.ATTRIBUTE_USER: user,
                                  StageManager.ATTRIBUTE_STATUS: status,
                                  StageManager.ATTRIBUTE_SECURITY_GROUP: security_group
                                },
                                True) # Replace



  def tag_instance(self, instance_id, attribute_name, attribute_value):
    """
    Add a tag to an instance

    @param instance_id Instance id as a string
    @param attribute_name Attribute name
    @param attribute_value Attribute Value
    @return Bool Whether the operation has succeeded or not
    """

    return self.sdb_connection.put_attributes(StageManager.STAGE_MANAGER_DOMAIN,
                                instance_id,
                                {
                                  attribute_name: attribute_value,
                                },
                                True) # Replace



  def get_all_instances(self):
    """
    Get all known staging servers
    """

    return self.sdb_connection.select(StageManager.STAGE_MANAGER_DOMAIN,
                          "select * from " + StageManager.STAGE_MANAGER_DOMAIN)


  def get_instance(self, instance_id):
    """
    Get specific staging server attributes
    """

    return self.sdb_connection.get_attributes(StageManager.STAGE_MANAGER_DOMAIN, instance_id)


  def delete_instance(self, instance_id):
    """
    Remove an instance from the known ones

    @param instance_id Instance id as a string
    @return Bool Whether the operation has succeeded or not
    """
    
    return self.sdb_connection.delete_attributes(StageManager.STAGE_MANAGER_DOMAIN,
                                  instance_id,
                                  None) # All attributes are to be deleted


  def get_instances_for_user(self, user):
    """
    Retrieve all staging instances belonging to a specific user

    @param user Owner of staging instances
    @return List of string of Amazon instances
    """

    result = self.sdb_connection.select(StageManager.STAGE_MANAGER_DOMAIN,
                          "select " + StageManager.ATTRIBUTE_INSTANCE_ID + " from " + StageManager.STAGE_MANAGER_DOMAIN + ' where user="' + user + '"')

    return map(lambda item: item[StageManager.ATTRIBUTE_INSTANCE_ID], result)
    
