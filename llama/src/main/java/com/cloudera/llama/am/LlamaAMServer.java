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
package com.cloudera.llama.am;

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.yarn.YarnRMLlamaAMConnector;
import com.cloudera.llama.server.ClientNotificationService;
import com.cloudera.llama.server.NodeMapper;
import com.cloudera.llama.server.Security;
import com.cloudera.llama.server.ThriftEndPoint;
import com.cloudera.llama.server.ThriftServer;
import com.cloudera.llama.thrift.LlamaAMService;
import com.cloudera.llama.util.UUID;
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

public class LlamaAMServer extends
    ThriftServer<com.cloudera.llama.thrift.LlamaAMService.Processor>
    implements ClientNotificationService.UnregisterListener {
  private LlamaAM llamaAm;
  private ClientNotificationService clientNotificationService;
  private NodeMapper nodeMapper;
  private String httpJmx;
  private String httpLlama;

  public LlamaAMServer() {
    super("LlamaAM", AMServerConfiguration.class);
  }

  private JmxReporter reporter;
  private Server httpServer;

  protected void startJMX() {
    reporter = JmxReporter.forRegistry(getMetricRegistry()).build();
    reporter.start();
  }

  protected String getHttpJmxEndPoint() {
    return httpJmx;
  }

  protected String getHttpLlamaUI() {
    return httpLlama;
  }

  protected void stopJMX() {
    reporter.stop();
    reporter.close();
  }

  private void startHttpServer() {
    httpServer = new Server();
    String strAddress = getServerConf().getHttpAddress();
    InetSocketAddress address = NetUtils.createSocketAddr(strAddress,
        getServerConf().getHttpDefaultPort());
    Connector connector = new SocketConnector();
    connector.setHost(address.getHostName());
    connector.setPort(address.getPort());
    httpServer.setConnectors(new Connector[]{connector});

    Context context = new Context();
    context.setContextPath("");
    context.setAttribute("hadoop.conf", new Configuration());
    context.addServlet(JMXJsonServlet.class, "/jmx");
    context.addServlet(LlamaServlet.class, "/*");
    httpServer.addHandler(context);

    try {
      httpServer.start();
      httpJmx = "http://" + getHostname(connector.getHost()) + ":" +
          connector.getLocalPort() + "/jmx";
      httpLlama = "http://" + getHostname(connector.getHost()) + ":" +
          connector.getLocalPort() + "/";

      getLog().info("HTTP JSON JMX     : {}", httpJmx);
      getLog().info("HTTP Llama Web UI : {}", httpLlama);
    } catch (Throwable ex) {
      throw new RuntimeException(ex);
    }
  }

  private void stopHttpServer() {
    try {
      httpServer.stop();
    } catch (Throwable ex) {
      getLog().warn("Error shutting down HTTP server, {}", ex.toString(), ex);
    }
  }

  @Override
  protected void startService() {
    startHttpServer();
    try {
      Security.loginToHadoop(getServerConf());
      Class<? extends NodeMapper> klass = getServerConf().getNodeMappingClass();
      nodeMapper = ReflectionUtils.newInstance(klass, getConf());
      clientNotificationService = new ClientNotificationService(getServerConf(),
          nodeMapper, getMetricRegistry(), this);
      clientNotificationService.start();

      getConf().set(YarnRMLlamaAMConnector.ADVERTISED_HOSTNAME_KEY,
          ThriftEndPoint.getServerAddress(getServerConf()));
      getConf().setInt(YarnRMLlamaAMConnector.ADVERTISED_PORT_KEY,
          ThriftEndPoint.getServerPort(getServerConf()));
      getConf().set(YarnRMLlamaAMConnector.ADVERTISED_TRACKING_URL_KEY,
          getHttpLlamaUI());
      llamaAm = LlamaAM.create(getConf());
      llamaAm.setMetricRegistry(getMetricRegistry());
      llamaAm.start();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void stopService() {
    llamaAm.stop();
    clientNotificationService.stop();
    stopHttpServer();
  }

  @Override
  protected LlamaAMService.Processor createServiceProcessor() {
    LlamaAMService.Iface handler = new LlamaAMServiceImpl(llamaAm, nodeMapper,
        clientNotificationService);
    MetricLlamaAMService.registerMetric(getMetricRegistry());
    handler = new MetricLlamaAMService(handler, getMetricRegistry());
    return new LlamaAMService.Processor<LlamaAMService.Iface>(handler);
  }

  @Override
  public void onUnregister(UUID handle) {
    try {
      llamaAm.releaseReservationsForClientId(handle);
    } catch (Throwable ex) {
      getLog().warn("Error releasing reservations for clientId '{}', {}",
          handle, ex.toString(), ex);
    }
  }
}
