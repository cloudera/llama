# Copyright (c) 2010 Cloudera, inc.
# https://wiki.cloudera.com/display/PRODUCT/Staging+step

import boto.sdb.connection
import boto


class ElasticIpManager:
  '''
  Class used for managing the pool of Elastic IP Addresses that can be associated
  to a staging instance
  '''

  # Amazon SDB domain
  EIMDOMAIN = "ElasticIpManagerDomain"

  # Attributes used to store information about elastic ips
  ATTRIBUTE_DNS = "dns"
  ATTRIBUTE_IP = "ip"


  def __init__(self):
    """
    Initialize connections to Amazon services
    """

    self.sdb_connection = boto.sdb.connection.SDBConnection()
    self.ec2_connection = boto.connect_ec2()


  def init_db(self):
    """
    Delete domain and recreate a blank one
	 Note: All data will be lost and unrecoverable. Be cautious.
    """

    # First step: Delete data and domain if it exists
    if self.sdb_connection.lookup(ElasticIpManager.EIMDOMAIN):
      self.sdb_connection.delete_domain(ElasticIpManager.EIMDOMAIN)

    # Second step: Recreate domain
    self.sdb_connection.create_domain(ElasticIpManager.EIMDOMAIN)


  def get_all_ips(self):
    """
    Get all ips whether they are used or not
    """
    return self.sdb_connection.select(ElasticIpManager.EIMDOMAIN,
                          "select * from " + ElasticIpManager.EIMDOMAIN)


  def add_ip(self, ip_address, dns):
    """
    Add a new address to the pool of available ones

    @param ip_address IP address
    @param dns DNS entry associated with ip_address (example: test02.cloudera.com)
    @return Bool Whether the operation has succeeded or not
    """
    return self.sdb_connection.put_attributes(ElasticIpManager.EIMDOMAIN,
                                ip_address,
                                {
                                  ElasticIpManager.ATTRIBUTE_DNS: dns,
                                  ElasticIpManager.ATTRIBUTE_IP: ip_address
                                },
                                True) # Replace


  def delete_ip(self, ip_address):
    """
    Remove a new address from the pool of available ones

    @param ip_address IP address
    @return Bool Whether the operation has succeeded or not
    """
    return self.sdb_connection.delete_attributes(ElasticIpManager.EIMDOMAIN, ip_address)


  def get_available_ips(self):
    """
    Return any available IP address not associated to any instance from the pool
    @return List of boto.ec2.address
    """

    # Get all ips from the pool
    ips_info = self.get_all_ips()

    if len(ips_info) > 0:
      # Keeps only the ip attribute
      ips = [ip_info[ElasticIpManager.ATTRIBUTE_IP] for ip_info in ips_info]

      # Convert them into boto.ec2.address objects
      eips = self.ec2_connection.get_all_addresses(addresses=ips)

      # Filter out the used ones
      return filter(lambda eip: eip.instance_id == '', eips)

    else:
      return []


  def get_first_available_ip(self):
    """
    Return the first available address

    @return First available address
    """

    available_ips = self.get_available_ips()
    if len(available_ips) > 0:
      return available_ips[0]
    else:
      return None
