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

import com.cloudera.llama.am.LlamaAM;
import com.cloudera.llama.am.yarn.YarnRMLlamaAMConnector;
import com.cloudera.llama.thrift.LlamaAMService;
import com.codahale.metrics.JmxReporter;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.jmx.JMXJsonServlet;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.ReflectionUtils;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;

import java.net.InetSocketAddress;

public class LlamaAMThriftServer extends 
    ThriftServer<LlamaAMService.Processor> {
  private LlamaAM llamaAm;
  private ClientNotificationService clientNotificationService;
  private ClientNotifier clientNotifier;
  private NodeMapper nodeMapper;
  private String httpJmxEndPoint;

  public LlamaAMThriftServer() {
    super("LlamaAM");
  }

  private JmxReporter reporter;
  private Server httpServer;

  protected void startJMX() {
    reporter = JmxReporter.forRegistry(getMetrics()).build();
    reporter.start();
    httpServer = new Server();
    String strAddress = getConf().get(ServerConfiguration.HTTP_JMX_ADDRESS_KEY,
        ServerConfiguration.HTTP_JMX_ADDRESS_DEFAULT);
    InetSocketAddress address = NetUtils.createSocketAddr(strAddress,
        ServerConfiguration.HTTP_JMX_PORT_DEFAULT);
    Connector connector = new SocketConnector();
    connector.setHost(address.getHostName());
    connector.setPort(address.getPort());
    httpServer.setConnectors(new Connector[]{connector});

    Context context = new Context();
    context.setContextPath("");
    context.setAttribute("hadoop.conf", new Configuration());
    context.addServlet(JMXJsonServlet.class, "/jmx");
    httpServer.addHandler(context);

    try {
      httpServer.start();
      httpJmxEndPoint = "http://" + connector.getHost() + ":" +
          connector.getLocalPort() + "/jmx";
      getLog().info("HTTP JSON JMX endpoint at: {}", httpJmxEndPoint);
    } catch (Throwable ex) {
      throw new RuntimeException(ex);
    }
  }

  protected String getHttpJmxEndPoint() {
    return httpJmxEndPoint;
  }

  protected void stopJMX() {
    try {
      httpServer.stop();
    } catch (Throwable ex) {
      getLog().warn("Error while shutting down HTTP JSON JMX, {}",
          ex.toString(), ex);
    }
    reporter.stop();
    reporter.close();
  }

  @Override
  protected void startService() {
    try {
      Security.loginToHadoop(getConf());
      Class<? extends NodeMapper> klass = getConf().getClass(
          ServerConfiguration.NODE_NAME_MAPPING_CLASS_KEY,
          ServerConfiguration.NODE_NAME_MAPPING_CLASS_DEFAULT,
          NodeMapper.class);
      nodeMapper = ReflectionUtils.newInstance(klass, getConf());
      clientNotificationService = new ClientNotificationService(getConf());
      clientNotifier = new ClientNotifier(getConf(), nodeMapper, 
          clientNotificationService);

      getConf().set(YarnRMLlamaAMConnector.ADVERTISED_HOSTNAME_KEY, 
          ThriftEndPoint.getServerAddress(getConf()));
      getConf().set(YarnRMLlamaAMConnector.ADVERTISED_TRACKING_URL_KEY, 
          YarnRMLlamaAMConnector.NOT_AVAILABLE_VALUE);
      llamaAm = LlamaAM.create(getConf());      
      clientNotifier.start();
      llamaAm.start();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void stopService() {
    llamaAm.stop();
    clientNotifier.stop();
  }

  @Override
  protected LlamaAMService.Processor createServiceProcessor() {
    LlamaAMService.Iface handler = new LlamaAMServiceImpl(llamaAm, nodeMapper,
        clientNotificationService, clientNotifier);
    return new LlamaAMService.Processor<LlamaAMService.Iface>(handler);
  }

}
