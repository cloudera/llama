# (c) Copyright 2008 Cloudera, Inc.
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
"""
A set of functions that are commonly used for this project
"""
import datetime
import time

def get_key_cols(dict):
  """
  takes a dict and returns a string to
  be added to an "insert" statement, where
  each item is a key column name

  Replaces:
   - '-' with '_'
   - ' ' with '_'
   and makes everything lowercase

  @type  dict: dictionary
  @param dict: the dictionary whose keys
               will be returned in preparation
               for a SQL INSERT statement
  @rtype     : string
  @return    : a comma-delimited string, where
               each item is a SQL-compliant key
               name
  """
  keys = []
  for key in dict:
    key = key.replace("-", "_")
    key = key.replace(" ", "_")
    key = key.lower()
    keys.append(key)

  return ','.join(keys)

def get_values(dict):
  """
  takes a dict and returns a string to
  be added to an "insert" statement, where
  each item is an escaped value.  Values
  are enclosed by single quotes (')

  Replaces:
   - "'" with "\'"

  @type  dict: dictionary
  @param dict: the dictionary whose values
               will be returned in preparation
               for a SQL INSERT statement
  @rtype     : string
  @return    : a comma-delimited string, where
               each item is a SQL-compliant value
  """
  values = []
  for key in dict:
    value = dict[key]
    # make sure to only escape strings to avoid
    # SQL injection
    if isinstance(value, (str)):
      value = value.replace("'", "\\'")
    value = "'" + str(value) + "'"
    values.append(value)

  return ','.join(values)

def get_threshold_date(seconds):
  """
  Given a number of seconds, returns a date
  that is today - seconds.  The date is in
  SQL format.

  @type  seconds: number
  @param seconds: the number of seconds, or the
                  threshold
  @rtype        : string (MySQL datetime format)
  @return       : a string that is I{now} - seconds
  """
  format = "%Y-%m-%d %H:%M:%S"

  today = str(datetime.datetime.today())
  today = today.split(".")[0]
  now = time.mktime(time.strptime(today, format))
  then = now - seconds

  thres = time.strftime(format, time.localtime(then))

  return thres

def store_orm(table, dict, conn):
    """
    Given statitistics, store them in to MySQL
    with an ORM mechanism

    Will convert keys to MySQL-compliant column
    names.  See I{get_key_cols(dict)}

    @type  table: string
    @param table: the SQL table
    @type  dict : dictionary
    @param dict : the dictionary to insert
    @type  conn : connection
    @param conn : a DB connection
    """
    c = conn.cursor()

    # ORM it!
    sql = "insert into %(table)s (%(cols)s) values \
                      (%(vals)s)" % \
                      {'table': table,
                       'cols': get_key_cols(dict),
                       'vals': get_values(dict)}

    c.execute(sql)

    conn.commit()
    c.close()
