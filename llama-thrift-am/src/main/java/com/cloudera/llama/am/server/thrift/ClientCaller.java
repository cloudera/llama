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

import com.cloudera.llama.thrift.LlamaNotificationService;
import org.apache.hadoop.conf.Configuration;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import java.util.UUID;

public class ClientCaller {  
  private final Configuration conf;
  private final String clientId;
  private final UUID handle;
  private final String host;
  private final int port;
  private TTransport tTransport;
  private LlamaNotificationService.Iface client;
  private boolean lastSuccessful;
  
  public ClientCaller(Configuration conf, String clientId, UUID handle, 
      String host, int port) {
    this.conf = conf;
    this.clientId = clientId;
    this.handle = handle;
    this.host = host;
    this.port = port;
  }
  
  public String getClientId() {
    return clientId;
  }
  
  public static abstract class Callable<T> implements 
      java.util.concurrent.Callable<T> {
    private UUID handle;
    private LlamaNotificationService.Iface client;

    protected UUID getHandle() {
      return handle;
    }
    
    protected LlamaNotificationService.Iface getClient() {
      return client;
    }

    public abstract T call() throws ClientException;
  }
  
  public synchronized <T> T execute(Callable<T> callable) 
      throws ClientException {
    T ret;
    try {
      if (!lastSuccessful) {
        client = createClient();
      }
      callable.handle = handle;
      callable.client = client;
      ret = callable.call();
      lastSuccessful = true;
    } catch (Exception ex) {
      lastSuccessful = false;
      throw new ClientException(ex);
    }
    return ret;
  }


  LlamaNotificationService.Iface createClient() throws Exception {    
    if (conf.getBoolean(ServerConfiguration.SECURITY_ENABLED_KEY,
        ServerConfiguration.SECURITY_ENABLED_DEFAULT)) {
      //TODO
      throw new UnsupportedOperationException("Security not implemented yet");
    } else {
      tTransport = new TSocket(host, port);
      tTransport.open();
    }
    TProtocol protocol = new TBinaryProtocol(tTransport);
    return new LlamaNotificationService.Client(protocol);
  }
  
  public synchronized void cleanUpClient() {
    if (tTransport != null) {
      tTransport.close();
    }
    tTransport = null;
    client = null;
  }
  
}
