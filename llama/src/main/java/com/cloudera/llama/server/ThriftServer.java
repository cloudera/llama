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
package com.cloudera.llama.server;

import com.cloudera.llama.am.impl.FastFormat;
import com.cloudera.llama.util.NamedThreadFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.thrift.TProcessor;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportFactory;

import javax.security.auth.Subject;
import java.net.InetAddress;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class ThriftServer<T extends TProcessor> extends
    AbstractServer {
  private Class<? extends ServerConfiguration> serverConfClass;
  private ServerConfiguration sConf;
  private TServer tServer;
  private TServerSocket tServerSocket;
  private Subject subject;
  private String hostname;
  private int port;

  protected ThriftServer(String serviceName,
      Class<? extends ServerConfiguration> serverConfClass) {
    super(serviceName);
    this.serverConfClass = serverConfClass;
  }

  protected ServerConfiguration getServerConf() {
    return sConf;
  }

  @Override
  public void setConf(Configuration conf) {
    if (conf.get(ServerConfiguration.CONFIG_DIR_KEY) == null) {
      throw new RuntimeException(FastFormat.format(
          "Required configuration property '{}' missing",
          ServerConfiguration.CONFIG_DIR_KEY));
    }
    super.setConf(conf);
    sConf = ReflectionUtils.newInstance(serverConfClass, conf);
  }

  ExecutorService createExecutorService(int minThreads, final int maxThreads, 
      final int queueSize) {
    BlockingQueue<Runnable> queue = 
        new LinkedBlockingQueue<Runnable>(queueSize);
    ThreadPoolExecutor executor = new ThreadPoolExecutor(minThreads, maxThreads,
        60, TimeUnit.SECONDS, queue, new NamedThreadFactory("llama-thrift"));
    executor.prestartAllCoreThreads();
    executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
      @Override
      public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        getLog().error("Discarding incoming calls, using maximum threads " +
            "'{}' and calls queue if full with '{}' entries", maxThreads, 
            queueSize);
      }
    });
    //TODO: add metric gauge for the queue and the executor active threads.
    return executor;
  }
  
  @Override
  protected void startTransport() {
    try {
      subject = Security.loginServerSubject(sConf);
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          int minThreads = sConf.getServerMinThreads();
          int maxThreads = sConf.getServerMaxThreads();
          int queueSize = sConf.getServerCallsQueueSize();
          tServerSocket = ThriftEndPoint.createTServerSocket(sConf);
          TTransportFactory tTransportFactory = ThriftEndPoint
              .createTTransportFactory(sConf);
          TProcessor processor = createServiceProcessor();
          processor = ThriftEndPoint.getAuthorizationTProcessor(sConf, false,
              processor);
          TThreadPoolServer.Args args = new TThreadPoolServer.Args
              (tServerSocket);
          args.executorService(createExecutorService(minThreads, maxThreads, 
              queueSize));
          args.transportFactory(tTransportFactory);          
          args.processor(processor);
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
  public synchronized String getAddressHost() {
    if (hostname == null) {
      hostname = (tServerSocket != null &&
          tServerSocket.getServerSocket().isBound())
                 ? getHostname(tServerSocket.getServerSocket().
          getInetAddress().getHostName())
                 : null;
    }
    return hostname;
  }

  @Override
  public synchronized int getAddressPort() {
    if (port == 0) {
      port = (tServerSocket != null &&
          tServerSocket.getServerSocket().isBound())
             ? tServerSocket.getServerSocket().getLocalPort() : 0;
    }
    return port;
  }

  protected abstract T createServiceProcessor();

  public static String getHostname(String address) {
    try {
      if (address.startsWith("0.0.0.0")) {
        address = InetAddress.getLocalHost().getCanonicalHostName();
      } else {
        int i = address.indexOf(":");
        if (i > -1) {
          address = address.substring(0, i);
        }
      }
      return address;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

}
