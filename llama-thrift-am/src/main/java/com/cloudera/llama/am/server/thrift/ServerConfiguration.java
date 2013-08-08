/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.llama.am.server.thrift;

import com.cloudera.llama.am.server.Main;

public interface ServerConfiguration {
  public static String CONFIG_DIR_KEY = Main.CONF_DIR_SYS_PROP;
  
  public static String KEY_PREFIX = "llama.am.server.thrift.";

  public static String SERVER_MIN_THREADS_KEY = KEY_PREFIX + 
      "server.min.threads";
  public static int SERVER_MIN_THREADS_DEFAULT = 10;

  public static String SERVER_MAX_THREADS_KEY = KEY_PREFIX + 
      "server.max.threads";
  public static int SERVER_MAX_THREADS_DEFAULT = 50;

  public static String SECURITY_ENABLED_KEY = KEY_PREFIX + "security";
  public static boolean SECURITY_ENABLED_DEFAULT = false;

  public static String SERVER_ADDRESS_KEY = KEY_PREFIX + "address";
  public static String SERVER_ADDRESS_DEFAULT = "0.0.0.0";
  public static int SERVER_PORT_DEFAULT = 15000;

  public static String CLIENT_NOTIFIER_QUEUE_THRESHOLD_KEY = KEY_PREFIX + 
      "client.notifier.queue.threshold";
  public static int CLIENT_NOTIFIER_QUEUE_THRESHOLD_DEFAULT = 10000;

  public static String CLIENT_NOTIFIER_THREADS_KEY = KEY_PREFIX + 
      "client.notifier.threads";
  public static int CLIENT_NOTIFER_THREADS_DEFAULT = 10;

  public static String CLIENT_NOTIFIER_MAX_RETRIES_KEY = KEY_PREFIX + 
      "client.notifier.max.retries";
  public static int CLIENT_NOTIFIER_MAX_RETRIES_DEFAULT = 5;

  public static String CLIENT_NOTIFIER_RETRY_INTERVAL_KEY = KEY_PREFIX + 
      "client.notifier.retry.interval.ms";
  public static int CLIENT_NOTIFIER_RETRY_INTERVAL_DEFAULT = 5000;


  public static String TRANSPORT_TIMEOUT_KEY = KEY_PREFIX +
      "transport.timeout.ms";
  public static int TRANSPORT_TIMEOUT_DEFAULT = 1000;

  public static String KEYTAB_FILE_KEY = KEY_PREFIX + "keytab.file";
  public static String KEYTAB_FILE_DEFAULT = "llama.keytab";

  public static String SERVER_PRINCIPAL_NAME_KEY = KEY_PREFIX +
      "server.principal.name";
  public static String SERVER_PRINCIPAL_NAME_DEFAULT = "llama";

  public static String NOTIFICATION_PRINCIPAL_NAME_KEY = KEY_PREFIX +
      "notification.principal.name";
  public static String NOTIFICATION_PRINCIPAL_NAME_DEFAULT = "impala";

}
