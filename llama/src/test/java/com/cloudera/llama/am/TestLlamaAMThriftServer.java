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
import com.cloudera.llama.am.impl.FastFormat;
import com.cloudera.llama.am.impl.GangAntiDeadlockLlamaAM;
import com.cloudera.llama.am.impl.SingleQueueLlamaAM;
import com.cloudera.llama.am.mock.mock.MockLlamaAMFlags;
import com.cloudera.llama.am.mock.mock.MockRMLlamaAMConnector;
import com.cloudera.llama.am.spi.RMLlamaAMConnector;
import com.cloudera.llama.server.ClientNotificationService;
import com.cloudera.llama.server.ClientNotifier;
import com.cloudera.llama.server.MetricClientLlamaNotificationService;
import com.cloudera.llama.server.NotificationEndPoint;
import com.cloudera.llama.server.ServerConfiguration;
import com.cloudera.llama.server.TestAbstractMain;
import com.cloudera.llama.server.TypeUtils;
import com.cloudera.llama.thrift.TLlamaAMGetNodesRequest;
import com.cloudera.llama.thrift.TLlamaAMGetNodesResponse;
import com.cloudera.llama.thrift.TLlamaAMRegisterRequest;
import com.cloudera.llama.thrift.TLlamaAMRegisterResponse;
import com.cloudera.llama.thrift.TLlamaAMReleaseRequest;
import com.cloudera.llama.thrift.TLlamaAMReleaseResponse;
import com.cloudera.llama.thrift.TLlamaAMReservationRequest;
import com.cloudera.llama.thrift.TLlamaAMReservationResponse;
import com.cloudera.llama.thrift.TLlamaAMUnregisterRequest;
import com.cloudera.llama.thrift.TLlamaAMUnregisterResponse;
import com.cloudera.llama.thrift.TLlamaServiceVersion;
import com.cloudera.llama.thrift.TLocationEnforcement;
import com.cloudera.llama.thrift.TNetworkAddress;
import com.cloudera.llama.thrift.TResource;
import com.cloudera.llama.thrift.TStatusCode;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import javax.security.auth.Subject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestLlamaAMThriftServer {
  private ServerConfiguration sConf =
      new AMServerConfiguration(new Configuration(false));

  public static class NotificationServerConfiguration
      extends ServerConfiguration {

    public NotificationServerConfiguration() {
      super("client", new Configuration(false));
    }

    @Override
    public int getThriftDefaultPort() {
      return 0;
    }

    @Override
    public int getHttpDefaultPort() {
      return 0;
    }
  }

  protected Configuration createCallbackConfiguration() throws Exception {
    ServerConfiguration cConf = new NotificationServerConfiguration();
    Configuration conf = new Configuration(false);
    conf.set(ServerConfiguration.CONFIG_DIR_KEY, TestAbstractMain.createTestDir());
    conf.set(cConf.getPropertyName(ServerConfiguration.SERVER_ADDRESS_KEY),
        "localhost:0");
    conf.set(cConf.getPropertyName(ServerConfiguration.HTTP_ADDRESS_KEY),
        "localhost:0");
    return conf;
  }


  protected Configuration createLlamaConfiguration() throws Exception {
    Configuration conf = new Configuration(false);
    conf.set(ServerConfiguration.CONFIG_DIR_KEY, TestAbstractMain.createTestDir());

    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MockRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "q1,q2");
    conf.set(MockRMLlamaAMConnector.QUEUES_KEY, "q1,q2");
    conf.set(MockRMLlamaAMConnector.NODES_KEY, "n1,n2");
    conf.setInt(MockRMLlamaAMConnector.EVENTS_MIN_WAIT_KEY, 5);
    conf.setInt(MockRMLlamaAMConnector.EVENTS_MAX_WAIT_KEY, 10);

    conf.set(sConf.getPropertyName(ServerConfiguration.SERVER_ADDRESS_KEY),
        "localhost:0");
    conf.set(sConf.getPropertyName(ServerConfiguration.HTTP_ADDRESS_KEY),
        "localhost:0");
    return conf;
  }

  @Test
  public void testStartStop() throws Exception {
    LlamaAMServer server = new LlamaAMServer();
    try {
      server.setConf(createLlamaConfiguration());
      server.start();
      Assert.assertNotSame(0, server.getAddressPort());
      Assert.assertEquals("localhost", server.getAddressHost());
      Assert.assertNotNull(server.getHttpJmxEndPoint());
      HttpURLConnection conn = (HttpURLConnection)
          new URL(server.getHttpJmxEndPoint()).openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());

      Assert.assertNotNull(server.getHttpLlamaUI());
      conn = (HttpURLConnection) new URL(server.getHttpLlamaUI()).
          openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
      conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() + "foo").
          openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_NOT_FOUND,
          conn.getResponseCode());
      conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() +
          "loggers").openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
      conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() +
          "json").openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
      conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() +
          "json/v1").openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
      conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() +
          "json/v1/summary").openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
      conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() +
          "json/v1/all").openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    } finally {
      server.stop();
    }
  }

  @Test
  public void testRegister() throws Exception {
    final LlamaAMServer server = new LlamaAMServer();
    try {
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client = createClient(server);


          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname("localhost");
          tAddress.setPort(0);
          trReq.setNotification_callback_service(tAddress);

          //register
          TLlamaAMRegisterResponse trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK, trRes.getStatus().getStatus_code());
          Assert.assertNotNull(trRes.getAm_handle());
          Assert.assertNotNull(TypeUtils.toUUID(trRes.getAm_handle()));

          //valid re-register
          trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK, trRes.getStatus().getStatus_code());
          Assert.assertNotNull(trRes.getAm_handle());
          Assert.assertNotNull(TypeUtils.toUUID(trRes.getAm_handle()));

          HttpURLConnection conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() +
              "json/v1/handle/" + TypeUtils.toUUID(trRes.getAm_handle()).toString()).openConnection();
          Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());

          //invalid re-register different address
          tAddress.setPort(1);
          trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.RUNTIME_ERROR, trRes.getStatus().
              getStatus_code());
          return null;
        }
      });
    } finally {
      server.stop();
    }
  }

  protected com.cloudera.llama.thrift.LlamaAMService.Client
  createClient(LlamaAMServer server)
      throws Exception {
    TTransport transport = new TSocket(server.getAddressHost(),
        server.getAddressPort());
    transport.open();
    TProtocol protocol = new TBinaryProtocol(transport);
    return new com.cloudera.llama.thrift.LlamaAMService.Client(protocol);
  }

  protected Subject getClientSubject() throws Exception {
    return new Subject();
  }

  @Test
  public void testUnregister() throws Exception {
    final LlamaAMServer server = new LlamaAMServer();
    try {
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client =
              createClient(server);

          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname("localhost");
          tAddress.setPort(0);
          trReq.setNotification_callback_service(tAddress);

          //register
          TLlamaAMRegisterResponse trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK, trRes.getStatus().
              getStatus_code());

          TLlamaAMUnregisterRequest turReq = new TLlamaAMUnregisterRequest();
          turReq.setVersion(TLlamaServiceVersion.V1);
          turReq.setAm_handle(trRes.getAm_handle());

          //valid unRegister
          TLlamaAMUnregisterResponse turRes = client.Unregister(turReq);
          Assert.assertEquals(TStatusCode.OK, turRes.getStatus().getStatus_code());

          //try call after unRegistered
          TLlamaAMGetNodesRequest tgnReq = new TLlamaAMGetNodesRequest();
          tgnReq.setVersion(TLlamaServiceVersion.V1);
          tgnReq.setAm_handle(trRes.getAm_handle());
          TLlamaAMGetNodesResponse tgnRes = client.GetNodes(tgnReq);
          Assert.assertEquals(TStatusCode.RUNTIME_ERROR, tgnRes.getStatus().
              getStatus_code());

          //valid re-unRegister
          turRes = client.Unregister(turReq);
          Assert.assertEquals(TStatusCode.OK, turRes.getStatus().getStatus_code());
          return null;
        }
      });
    } finally {
      server.stop();
    }
  }

  @Test
  public void testGetNodes() throws Exception {
    final LlamaAMServer server = new LlamaAMServer();
    try {
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client =
              createClient(server);

          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname("localhost");
          tAddress.setPort(0);
          trReq.setNotification_callback_service(tAddress);

          //register
          TLlamaAMRegisterResponse trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK, trRes.getStatus().
              getStatus_code());

          //getNodes
          TLlamaAMGetNodesRequest tgnReq = new TLlamaAMGetNodesRequest();
          tgnReq.setVersion(TLlamaServiceVersion.V1);
          tgnReq.setAm_handle(trRes.getAm_handle());
          TLlamaAMGetNodesResponse tgnRes = client.GetNodes(tgnReq);
          Assert.assertEquals(TStatusCode.OK, tgnRes.getStatus().getStatus_code());
          Assert.assertEquals(Arrays.asList("n1", "n2"), tgnRes.getNodes());

          //unregister
          TLlamaAMUnregisterRequest turReq = new TLlamaAMUnregisterRequest();
          turReq.setVersion(TLlamaServiceVersion.V1);
          turReq.setAm_handle(trRes.getAm_handle());
          TLlamaAMUnregisterResponse turRes = client.Unregister(turReq);
          Assert.assertEquals(TStatusCode.OK, turRes.getStatus().getStatus_code());
          return null;
        }
      });
    } finally {
      server.stop();
    }
  }

  private static class MyLlamaAMServer extends LlamaAMServer {

    @Override
    public MetricRegistry getMetricRegistry() {
      return super.getMetricRegistry();
    }
  }

  private void verifyMetricRegistration(MyLlamaAMServer server)
      throws Exception {
    Set<String> keys = new HashSet<String>();
    keys.addAll(ClientNotificationService.METRIC_KEYS);
    keys.addAll(ClientNotifier.METRIC_KEYS);
    keys.addAll(GangAntiDeadlockLlamaAM.METRIC_KEYS);
    keys.addAll(MetricClientLlamaNotificationService.METRIC_KEYS);
    keys.addAll(MetricLlamaAMService.METRIC_KEYS);
    for (String key : SingleQueueLlamaAM.METRIC_TEMPLATE_KEYS) {
      keys.add(FastFormat.format(key, "q1"));
    }
    MetricRegistry mr = server.getMetricRegistry();
    Map<String, Metric> metrics = mr.getMetrics();
    Assert.assertTrue(metrics.keySet().containsAll(keys));

  }

  @Test
  public void testReservation() throws Exception {
    final MyLlamaAMServer server = new MyLlamaAMServer();
    final NotificationEndPoint callbackServer = new NotificationEndPoint();
    try {
      callbackServer.setConf(createCallbackConfiguration());
      callbackServer.start();
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client = createClient(server);

          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname(callbackServer.getAddressHost());
          tAddress.setPort(callbackServer.getAddressPort());
          trReq.setNotification_callback_service(tAddress);

          //register
          TLlamaAMRegisterResponse trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK, trRes.getStatus().
              getStatus_code());

          //valid reservation
          TLlamaAMReservationRequest tresReq = new TLlamaAMReservationRequest();
          tresReq.setVersion(TLlamaServiceVersion.V1);
          tresReq.setAm_handle(trRes.getAm_handle());
          tresReq.setQueue("q1");
          TResource tResource = new TResource();
          tResource.setClient_resource_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
          tResource.setAskedLocation(MockLlamaAMFlags.ALLOCATE + "n1");
          tResource.setV_cpu_cores((short) 1);
          tResource.setMemory_mb(1024);
          tResource.setEnforcement(TLocationEnforcement.MUST);
          tresReq.setResources(Arrays.asList(tResource));
          tresReq.setGang(true);
          TLlamaAMReservationResponse tresRes = client.Reserve(tresReq);
          Assert.assertEquals(TStatusCode.OK, 
              tresRes.getStatus().getStatus_code());
          //check notification delivery
          Thread.sleep(300);
          Assert.assertEquals(1, callbackServer.notifications.size());

          HttpURLConnection conn = (HttpURLConnection) 
              new URL(server.getHttpLlamaUI() + "json/v1/reservation/" + 
                  TypeUtils.toUUID(tresRes.getReservation_id()).toString())
                  .openConnection();
          Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());

          conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() +
              "json/v1/queue/q1").openConnection();
          Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());

          conn = (HttpURLConnection) new URL(server.getHttpLlamaUI() +
              "json/v1/node/n1").openConnection();
          Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());

          //invalid reservation
          tresReq = new TLlamaAMReservationRequest();
          tresReq.setVersion(TLlamaServiceVersion.V1);
          tresReq.setAm_handle(trRes.getAm_handle());
          tresReq.setQueue("q1");
          tResource = new TResource();
          tResource.setClient_resource_id(TypeUtils.toTUniqueId(UUID
              .randomUUID()));
          tResource.setAskedLocation(MockLlamaAMFlags.ALLOCATE + "n1");
          tResource.setV_cpu_cores((short) 0);
          tResource.setMemory_mb(0);
          tResource.setEnforcement(TLocationEnforcement.MUST);
          tresReq.setResources(Arrays.asList(tResource));
          tresReq.setGang(true);
          tresRes = client.Reserve(tresReq);
          Assert.assertEquals(TStatusCode.RUNTIME_ERROR, tresRes.getStatus()
              .getStatus_code());
          Assert.assertTrue(tresRes.getStatus().getError_msgs().get(0).
              contains("IllegalArgumentException"));
          //check notification delivery
          Thread.sleep(300);
          Assert.assertEquals(1, callbackServer.notifications.size());

          //unregister
          TLlamaAMUnregisterRequest turReq = new TLlamaAMUnregisterRequest();
          turReq.setVersion(TLlamaServiceVersion.V1);
          turReq.setAm_handle(trRes.getAm_handle());
          TLlamaAMUnregisterResponse turRes = client.Unregister(turReq);
          Assert.assertEquals(TStatusCode.OK, 
              turRes.getStatus().getStatus_code());

          //test metric registration
          verifyMetricRegistration(server);
          return null;
        }
      });
    } finally {
      server.stop();
      callbackServer.stop();
    }
  }

  @Test
  public void testRelease() throws Exception {
    final LlamaAMServer server = new LlamaAMServer();
    final NotificationEndPoint callbackServer = new NotificationEndPoint();
    try {
      callbackServer.setConf(createCallbackConfiguration());
      callbackServer.start();
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client = createClient(server);

          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname(callbackServer.getAddressHost());
          tAddress.setPort(callbackServer.getAddressPort());
          trReq.setNotification_callback_service(tAddress);

          //register
          TLlamaAMRegisterResponse trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK, trRes.getStatus().
              getStatus_code());

          //reservation
          TLlamaAMReservationRequest tresReq = new TLlamaAMReservationRequest();
          tresReq.setVersion(TLlamaServiceVersion.V1);
          tresReq.setAm_handle(trRes.getAm_handle());
          tresReq.setQueue("q1");
          TResource tResource = new TResource();
          tResource.setClient_resource_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
          tResource.setAskedLocation(MockLlamaAMFlags.ALLOCATE + "n1");
          tResource.setV_cpu_cores((short) 1);
          tResource.setMemory_mb(1024);
          tResource.setEnforcement(TLocationEnforcement.MUST);
          tresReq.setResources(Arrays.asList(tResource));
          tresReq.setGang(true);
          TLlamaAMReservationResponse tresRes = client.Reserve(tresReq);
          Assert.assertEquals(TStatusCode.OK, tresRes.getStatus().getStatus_code());

          //check notification delivery
          Thread.sleep(300);
          Assert.assertEquals(1, callbackServer.notifications.size());

          //release
          TLlamaAMReleaseRequest trelReq = new TLlamaAMReleaseRequest();
          trelReq.setVersion(TLlamaServiceVersion.V1);
          trelReq.setAm_handle(trRes.getAm_handle());
          trelReq.setReservation_id(tresRes.getReservation_id());
          TLlamaAMReleaseResponse trelRes = client.Release(trelReq);
          Assert.assertEquals(TStatusCode.OK, trelRes.getStatus().getStatus_code());

          //unregister
          TLlamaAMUnregisterRequest turReq = new TLlamaAMUnregisterRequest();
          turReq.setVersion(TLlamaServiceVersion.V1);
          turReq.setAm_handle(trRes.getAm_handle());
          TLlamaAMUnregisterResponse turRes = client.Unregister(turReq);
          Assert.assertEquals(TStatusCode.OK, turRes.getStatus().getStatus_code());
          return null;
        }
      });
    } finally {
      server.stop();
      callbackServer.stop();
    }
  }

  @Test
  public void testDiscardReservationsOnMissingClient() throws Exception {
    final LlamaAMServer server = new LlamaAMServer();
    final NotificationEndPoint callbackServer = new NotificationEndPoint();
    try {
      callbackServer.setConf(createCallbackConfiguration());
      callbackServer.start();

      callbackServer.delayResponse = 250;

      Configuration conf = createLlamaConfiguration();
      conf.setInt(sConf.getPropertyName(
          ServerConfiguration.CLIENT_NOTIFIER_HEARTBEAT_KEY), 10000);
      conf.setInt(sConf.getPropertyName(
          ServerConfiguration.CLIENT_NOTIFIER_RETRY_INTERVAL_KEY), 200);
      conf.setInt(sConf.getPropertyName(
          ServerConfiguration.CLIENT_NOTIFIER_MAX_RETRIES_KEY), 0);
      conf.setInt(sConf.getPropertyName(
          ServerConfiguration.TRANSPORT_TIMEOUT_KEY), 200);
      server.setConf(conf);
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client = createClient(server);

          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname(callbackServer.getAddressHost());
          tAddress.setPort(callbackServer.getAddressPort());
          trReq.setNotification_callback_service(tAddress);

          //register
          TLlamaAMRegisterResponse trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK, trRes.getStatus().
              getStatus_code());

          //make reservation
          TLlamaAMReservationRequest tresReq = new TLlamaAMReservationRequest();
          tresReq.setVersion(TLlamaServiceVersion.V1);
          tresReq.setAm_handle(trRes.getAm_handle());
          tresReq.setQueue("q1");
          TResource tResource = new TResource();
          tResource.setClient_resource_id(
              TypeUtils.toTUniqueId(UUID.randomUUID()));
          tResource.setAskedLocation(MockLlamaAMFlags.ALLOCATE + "n1");
          tResource.setV_cpu_cores((short) 1);
          tResource.setMemory_mb(1024);
          tResource.setEnforcement(TLocationEnforcement.MUST);
          tresReq.setResources(Arrays.asList(tResource));
          tresReq.setGang(true);
          TLlamaAMReservationResponse tresRes = client.Reserve(tresReq);
          Assert.assertEquals(TStatusCode.OK,
              tresRes.getStatus().getStatus_code());

          Thread.sleep(250); //extra 50sec
          Assert.assertEquals(0, callbackServer.notifications.size());
          callbackServer.delayResponse = 0;

          //release
          client = createClient(server);
          TLlamaAMReleaseRequest trelReq = new TLlamaAMReleaseRequest();
          trelReq.setVersion(TLlamaServiceVersion.V1);
          trelReq.setAm_handle(trRes.getAm_handle());
          trelReq.setReservation_id(tresRes.getReservation_id());
          TLlamaAMReleaseResponse trelRes = client.Release(trelReq);
          Assert.assertEquals(TStatusCode.RUNTIME_ERROR, trelRes.getStatus()
              .getStatus_code());
          return null;
        }
      });
    } finally {
      server.stop();
      callbackServer.stop();
    }
  }

}
