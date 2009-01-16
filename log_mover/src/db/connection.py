# (c) Copyright 2009 Cloudera, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
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
