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
import com.cloudera.llama.am.mock.MockRMLlamaAMConnector;
import com.cloudera.llama.am.mock.MockLlamaAMFlags;
import com.cloudera.llama.am.server.TestMain;
import com.cloudera.llama.am.spi.RMLlamaAMConnector;
import com.cloudera.llama.thrift.LlamaAMService;
import com.cloudera.llama.thrift.LlamaNotificationService;
import com.cloudera.llama.thrift.TLlamaAMGetNodesRequest;
import com.cloudera.llama.thrift.TLlamaAMGetNodesResponse;
import com.cloudera.llama.thrift.TLlamaAMNotificationRequest;
import com.cloudera.llama.thrift.TLlamaAMNotificationResponse;
import com.cloudera.llama.thrift.TLlamaAMRegisterRequest;
import com.cloudera.llama.thrift.TLlamaAMRegisterResponse;
import com.cloudera.llama.thrift.TLlamaAMReleaseRequest;
import com.cloudera.llama.thrift.TLlamaAMReleaseResponse;
import com.cloudera.llama.thrift.TLlamaAMReservationRequest;
import com.cloudera.llama.thrift.TLlamaAMReservationResponse;
import com.cloudera.llama.thrift.TLlamaAMUnregisterRequest;
import com.cloudera.llama.thrift.TLlamaAMUnregisterResponse;
import com.cloudera.llama.thrift.TLlamaNMNotificationRequest;
import com.cloudera.llama.thrift.TLlamaNMNotificationResponse;
import com.cloudera.llama.thrift.TLlamaServiceVersion;
import com.cloudera.llama.thrift.TLocationEnforcement;
import com.cloudera.llama.thrift.TNetworkAddress;
import com.cloudera.llama.thrift.TResource;
import com.cloudera.llama.thrift.TStatusCode;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import javax.security.auth.Subject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TestLlamaAMThriftServer {
  
  protected Configuration createCallbackConfiguration() throws Exception {
    Configuration conf = new Configuration(false);
    conf.set(ServerConfiguration.CONFIG_DIR_KEY, TestMain.createTestDir());
    conf.set(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");
    return conf;
  }

 
  protected Configuration createLlamaConfiguration() throws Exception {
    Configuration conf = new Configuration(false);
    conf.set(ServerConfiguration.CONFIG_DIR_KEY, TestMain.createTestDir());

    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MockRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "q1,q2");
    conf.set(MockRMLlamaAMConnector.QUEUES_KEY, "q1,q2");
    conf.set(MockRMLlamaAMConnector.NODES_KEY, "n1,n2");
    conf.setInt(MockRMLlamaAMConnector.EVENTS_MIN_WAIT_KEY, 5);
    conf.setInt(MockRMLlamaAMConnector.EVENTS_MAX_WAIT_KEY, 10);

    conf.set(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");
    conf.set(ServerConfiguration.HTTP_JMX_ADDRESS_KEY, "localhost:0");
    return conf;
  }

  @Test
  public void testStartStop() throws Exception {
    LlamaAMThriftServer server = new LlamaAMThriftServer(); 
    try {
      server.setConf(createLlamaConfiguration());
      server.start();
      Assert.assertNotSame(0, server.getAddressPort());
      Assert.assertEquals("localhost", server.getAddressHost());
      Assert.assertNotNull(server.getHttpJmxEndPoint());
      HttpURLConnection conn = (HttpURLConnection)
          new URL(server.getHttpJmxEndPoint()).openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    } finally {
      server.stop();
    }
  }

  private static class NotificationEndPoint extends 
      ThriftServer<LlamaNotificationService.Processor> {
    private List<TLlamaAMNotificationRequest> notifications;

    protected NotificationEndPoint() {
      super("NotificationEndPoint");
      notifications = new ArrayList<TLlamaAMNotificationRequest>();
    }

    @Override
    protected LlamaNotificationService.Processor createServiceProcessor() {
      LlamaNotificationService.Iface handler = 
          new LlamaNotificationService.Iface() {
        @Override
        public TLlamaAMNotificationResponse AMNotification(
            TLlamaAMNotificationRequest request) throws TException {
          notifications.add(request);
          return new TLlamaAMNotificationResponse().setStatus(TypeUtils.OK);
        }

        @Override
        public TLlamaNMNotificationResponse NMNotification(
            TLlamaNMNotificationRequest request) throws TException {
          throw new UnsupportedOperationException();
        }
      };
      return new LlamaNotificationService.Processor<LlamaNotificationService
          .Iface>(handler);
    }

    @Override
    protected void startService() {
    }

    @Override
    protected void stopService() {
    }
  }
  
  @Test
  public void testRegister() throws Exception {
    final LlamaAMThriftServer server = new LlamaAMThriftServer();
    try {
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          LlamaAMService.Client client = createClient(server);


          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id("c1");
          TNetworkAddress  tAddress = new TNetworkAddress();
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
  
  protected LlamaAMService.Client createClient(LlamaAMThriftServer server) 
      throws Exception {
    TTransport transport = new TSocket(server.getAddressHost(),
        server.getAddressPort());
    transport.open();
    TProtocol protocol = new TBinaryProtocol(transport);
    return new LlamaAMService.Client(protocol);    
  }

  protected Subject getClientSubject() throws Exception {
    return new Subject();
  }
  
  @Test
  public void testUnregister() throws Exception {
    final LlamaAMThriftServer server = new LlamaAMThriftServer();
    try {
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          LlamaAMService.Client client = createClient(server);

          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id("c1");
          TNetworkAddress  tAddress = new TNetworkAddress();
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
    final LlamaAMThriftServer server = new LlamaAMThriftServer();
    try {
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          LlamaAMService.Client client = createClient(server);
    
          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id("c1");
          TNetworkAddress  tAddress = new TNetworkAddress();
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

  @Test
  public void testReservation() throws Exception {
    final LlamaAMThriftServer server = new LlamaAMThriftServer();
    final NotificationEndPoint callbackServer = new NotificationEndPoint();
    try {
      callbackServer.setConf(createCallbackConfiguration());
      callbackServer.start();
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          LlamaAMService.Client client = createClient(server);
    
          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id("c1");
          TNetworkAddress  tAddress = new TNetworkAddress();
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
          Assert.assertEquals(TStatusCode.OK, tresRes.getStatus().getStatus_code());
          //check notification delivery
          Thread.sleep(300);
          Assert.assertEquals(1, callbackServer.notifications.size());

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
  public void testRelease() throws Exception {
    final LlamaAMThriftServer server = new LlamaAMThriftServer();
    final NotificationEndPoint callbackServer = new NotificationEndPoint();
    try {
      callbackServer.setConf(createCallbackConfiguration());
      callbackServer.start();
      server.setConf(createLlamaConfiguration());
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          LlamaAMService.Client client = createClient(server);
    
          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id("c1");
          TNetworkAddress  tAddress = new TNetworkAddress();
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

}
