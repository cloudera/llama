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

import com.cloudera.llama.am.impl.FastFormat;
import com.cloudera.llama.am.server.AbstractServer;
import org.apache.hadoop.conf.Configuration;
import org.apache.thrift.TProcessor;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportFactory;

import javax.security.auth.Subject;
import java.security.PrivilegedExceptionAction;

public abstract class ThriftServer<T extends TProcessor> extends
    AbstractServer {
  private TServer tServer;
  private TServerSocket tServerSocket;
  private Subject subject;

  protected ThriftServer(String serviceName) {
    super(serviceName);
  }

  @Override
  public void setConf(Configuration conf) {
    if (conf.get(ServerConfiguration.CONFIG_DIR_KEY) == null) {
      throw new RuntimeException(FastFormat.format(
          "Required configuration property '{}' missing", 
          ServerConfiguration.CONFIG_DIR_KEY));
    }
    super.setConf(conf);
  }

  @Override
  protected void startTransport() {
    try {
      subject = Security.loginServerSubject(getConf());
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          int minThreads = getConf().getInt(ServerConfiguration
              .SERVER_MIN_THREADS_KEY, ServerConfiguration
              .SERVER_MIN_THREADS_DEFAULT);
          int maxThreads = getConf().getInt(ServerConfiguration
              .SERVER_MAX_THREADS_KEY, ServerConfiguration
              .SERVER_MAX_THREADS_DEFAULT);
          tServerSocket = ThriftEndPoint.createTServerSocket(getConf());
          TTransportFactory tTransportFactory = ThriftEndPoint
              .createTTransportFactory(getConf());
          T processor = createServiceProcessor();
          TThreadPoolServer.Args args = new TThreadPoolServer.Args
              (tServerSocket);
          args.transportFactory(tTransportFactory);
          args = args.minWorkerThreads(minThreads);
          args = args.maxWorkerThreads(maxThreads);
          args = args.processor(processor);
          tServer = new TThreadPoolServer(args);
          tServer.serve();
          return null;
        }
      });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void stopTransport() {
    tServer.stop();
    Security.logout(subject);
  }

  @Override
  public String getAddressHost() {
    return (tServerSocket != null && tServerSocket.getServerSocket().isBound()) 
           ? tServerSocket.getServerSocket().getInetAddress().getHostName() 
           : null;
  }

  @Override
  public int getAddressPort() {
    return (tServerSocket != null && tServerSocket.getServerSocket().isBound())
           ? tServerSocket.getServerSocket().getLocalPort() : 0;
  }
  
  protected abstract T createServiceProcessor();

}
