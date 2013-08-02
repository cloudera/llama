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

public interface ServerConfiguration {
  String KEY_PREFIX = "llama.am.server.thrift.";

  String SERVER_MIN_THREADS_KEY = KEY_PREFIX + "server.min.threads";
  int SERVER_MIN_THREADS_DEFAULT = 10;

  String SERVER_MAX_THREADS_KEY = KEY_PREFIX + "server.max.threads";
  int SERVER_MAX_THREADS_DEFAULT = 50;

  String SECURITY_ENABLED_KEY = KEY_PREFIX + "security";
  boolean SECURITY_ENABLED_DEFAULT = false;

  String SERVER_ADDRESS_KEY = KEY_PREFIX + "address";
  String SERVER_ADDRESS_DEFAULT = "0.0.0.0";
  int SERVER_PORT_DEFAULT = 15000;

  String CLIENT_NOTIFIER_QUEUE_THRESHOLD_KEY = KEY_PREFIX + 
      "client.notifier.queue.threshold";
  int CLIENT_NOTIFIER_QUEUE_THRESHOLD_DEFAULT = 10000;
  
  String CLIENT_NOTIFIER_THREADS_KEY = KEY_PREFIX + "client.notifier.threads";
  int CLIENT_NOTIFER_THREADS_DEFAULT = 10;
  
  String CLIENT_NOTIFIER_MAX_RETRIES_KEY = KEY_PREFIX + 
      "client.notifier.max.retries";
  int CLIENT_NOTIFIER_MAX_RETRIES_DEFAULT = 5;
  
  String CLIENT_NOTIFIER_RETRY_INTERVAL_KEY = KEY_PREFIX + 
      "client.notifier.retry.interval.ms";
  int CLIENT_NOTIFIER_RETRY_INTERVAL_DEFAULT = 5000;

}
