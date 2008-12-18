# (c) Copyright 2008 Cloudera, Inc.
import MySQLdb

import settings

HOST = settings.db_host
USER = settings.db_user
PASSWORD = settings.db_password
DATABASE = settings.db_database

def connect():
  """
  Establish and return a connection to
  a MySQL database

  @rtype : connection
  @return: a MySQL DB connection
  """
  conn = MySQLdb.connect(host = HOST,
                          user = USER,
                          passwd = PASSWORD,
                          db = DATABASE)

  return conn