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

import com.cloudera.llama.am.server.AbstractServer;
import org.apache.hadoop.net.NetUtils;
import org.apache.thrift.TProcessor;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

public abstract class ThriftServer<T extends TProcessor> extends 
    AbstractServer<T> {  
  private volatile TServer tServer;
  private TServerSocket tServerSocket;
  private String tServerAddressHost;
  private int tServerAddressPort;

  protected ThriftServer(String serviceName) {
    super(serviceName);
  }

  @Override
  protected void startTransport() {
    int minThreads = getConf().getInt(
        ServerConfiguration.SERVER_MIN_THREADS_KEY,
        ServerConfiguration.SERVER_MIN_THREADS_DEFAULT);
    int maxThreads = getConf().getInt(
        ServerConfiguration.SERVER_MAX_THREADS_KEY,
        ServerConfiguration.SERVER_MAX_THREADS_DEFAULT);
    try {
      TServerSocket serverTransport = createTServerSocket();

      T processor = createServiceProcessor();
      TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport);
      args = args.minWorkerThreads(minThreads);
      args = args.maxWorkerThreads(maxThreads);
      args = args.processor(processor);
      tServer = new TThreadPoolServer(args);
      tServer.serve();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void stopTransport() {
    tServer.stop();
  }

  @Override
  public String getAddressHost() {
    if (tServerAddressHost == null) {
      if (tServer != null && tServer.isServing()) {
        ServerSocket serverSocket = tServerSocket.getServerSocket();
        if (serverSocket.isBound()) {
          tServerAddressHost = serverSocket.getInetAddress().getHostName();
        }
      }
    }
    return tServerAddressHost;
  }

  @Override
  public int getAddressPort() {
    if (tServerAddressPort == 0) {
      if (tServer != null && tServer.isServing()) {
        ServerSocket serverSocket = tServerSocket.getServerSocket();
        if (serverSocket.isBound()) {
          tServerAddressPort = serverSocket.getLocalPort();
        }
      }
    }
    return tServerAddressPort;
  }

  private TServerSocket createTServerSocket() throws Exception {    
    String strAddress = getConf().get(ServerConfiguration.SERVER_ADDRESS_KEY, 
        ServerConfiguration.SERVER_ADDRESS_DEFAULT);
    InetSocketAddress address = NetUtils.createSocketAddr(strAddress, 
        ServerConfiguration.SERVER_PORT_DEFAULT);
    TServerSocket tServerSocket;
    if (getConf().getBoolean(ServerConfiguration.SECURITY_ENABLED_KEY, 
        ServerConfiguration.SECURITY_ENABLED_DEFAULT)) {
      //TODO
      throw new UnsupportedOperationException("Security not implemented yet");
    } else {
      tServerSocket = new TServerSocket(address);
    }
    this.tServerSocket = tServerSocket;
    return tServerSocket;
  }
  
  protected abstract T createServiceProcessor();

}
