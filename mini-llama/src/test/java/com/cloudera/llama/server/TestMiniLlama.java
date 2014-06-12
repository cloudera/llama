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

import com.cloudera.llama.am.MiniLlama;
import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.thrift.LlamaAMService;
import com.cloudera.llama.thrift.TLlamaAMGetNodesRequest;
import com.cloudera.llama.thrift.TLlamaAMGetNodesResponse;
import com.cloudera.llama.thrift.TLlamaAMNotificationRequest;
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
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class TestMiniLlama {

  protected String getUserName() {
    return System.getProperty("user.name");
  }

  @Test
  public void testMiniLlamaWithHadoopMiniCluster()
      throws Exception {
    testMiniLlamaWithHadoopMiniCluster(false);
  }

  @Test
  public void testMiniLlamaWithHadoopMiniClusterWriteHdfsConf()
      throws Exception {
    testMiniLlamaWithHadoopMiniCluster(true);
  }

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

  private void testMiniLlamaWithHadoopMiniCluster(boolean writeHdfsConf) 
      throws Exception {
    Configuration conf = MiniLlama.createMiniClusterConf(2);
    conf.set("yarn.scheduler.fair.allocation.file", "test-fair-scheduler.xml");
    conf.set(LlamaAM.CORE_QUEUES_KEY, "root.queue1");
    testMiniLlama(conf, writeHdfsConf);
  }

  private void testMiniLlama(Configuration conf, boolean writeHdfsConf) 
      throws Exception {
    File confFile = null;
    MiniLlama server = new MiniLlama(conf);
    final NotificationEndPoint callbackServer = new NotificationEndPoint();
    try {
      callbackServer.setConf(createCallbackConfiguration());
      callbackServer.start();
      Assert.assertNotNull(server.getConf().get(LlamaAM.CORE_QUEUES_KEY));
      if (writeHdfsConf) {
        File confDir = new File("target", UUID.randomUUID().toString());
        confDir.mkdirs();
        confFile = new File(confDir, "minidfs-site.xml").getAbsoluteFile();
        server.setWriteHadoopConfig(confFile.getAbsolutePath());
      }
      server.start();
      
      if (writeHdfsConf) {
        Assert.assertTrue(confFile.exists());
      }
      Assert.assertNotSame(0, server.getAddressPort());
      TTransport transport = new TSocket(server.getAddressHost(),
          server.getAddressPort());
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      LlamaAMService.Client client = new LlamaAMService.Client(protocol);

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

      //getNodes
      TLlamaAMGetNodesRequest tgnReq = new TLlamaAMGetNodesRequest();
      tgnReq.setVersion(TLlamaServiceVersion.V1);
      tgnReq.setAm_handle(trRes.getAm_handle());
      TLlamaAMGetNodesResponse tgnRes = client.GetNodes(tgnReq);
      Assert.assertEquals(TStatusCode.OK, tgnRes.getStatus().getStatus_code());
      Assert.assertEquals(new HashSet<String>(server.getDataNodes()),
          new HashSet<String>(tgnRes.getNodes()));

      reserveExpandRelease(trRes, server, client, callbackServer, 1, 74);
      reserveExpandRelease(trRes, server, client, callbackServer, 1, 0);
      reserveExpandRelease(trRes, server, client, callbackServer, 2, 74);
      reserveExpandRelease(trRes, server, client, callbackServer, 1, 0);

      //test MiniHDFS
      FileSystem fs = FileSystem.get(server.getConf());
      Assert.assertTrue(fs.getUri().getScheme().equals("hdfs"));
      fs.listStatus(new Path("/"));
      OutputStream os = fs.create(new Path("/test.txt"));
      os.write(0);
      os.close();

      //unregister
      TLlamaAMUnregisterRequest turReq = new TLlamaAMUnregisterRequest();
      turReq.setVersion(TLlamaServiceVersion.V1);
      turReq.setAm_handle(trRes.getAm_handle());
      TLlamaAMUnregisterResponse turRes = client.Unregister(turReq);
      Assert.assertEquals(TStatusCode.OK, turRes.getStatus().getStatus_code());
    } finally {
      server.stop();
      callbackServer.stop();
    }
  }

  private void reserveExpandRelease(TLlamaAMRegisterResponse trRes, MiniLlama
      server, LlamaAMService.Client client, NotificationEndPoint callbackServer,
                                   int cpusAsk, int memoryAsk) throws
      InterruptedException, TException {
    //reserve
    TLlamaAMReservationRequest tresReq = new TLlamaAMReservationRequest();
    tresReq.setVersion(TLlamaServiceVersion.V1);
    tresReq.setAm_handle(trRes.getAm_handle());
    tresReq.setUser(getUserName());
    tresReq.setQueue("queue1");
    tresReq.setReservation_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
    TResource tResource = new TResource();
    tResource.setClient_resource_id(TypeUtils.toTUniqueId(UUID.randomUUID()));
    tResource.setAskedLocation(server.getDataNodes().get(0));
    tResource.setV_cpu_cores((short) cpusAsk);
    tResource.setMemory_mb(memoryAsk);
    tResource.setEnforcement(TLocationEnforcement.MUST);
    tresReq.setResources(Arrays.asList(tResource));
    tresReq.setGang(true);
    TLlamaAMReservationResponse tresRes = client.Reserve(tresReq);
    Assert.assertEquals(TStatusCode.OK, tresRes.getStatus().getStatus_code());
    //check notification delivery
    while (callbackServer.notifications.isEmpty()) {
      Thread.sleep(300);
    }
    boolean allocated = false;
    while (!allocated) {
      List<TLlamaAMNotificationRequest> list =
          new ArrayList<TLlamaAMNotificationRequest>(callbackServer
              .notifications);
      for (TLlamaAMNotificationRequest notif : list) {
        if (notif.isSetAllocated_resources()) {
          allocated = true;
        }
      }
      if (!allocated) {
        Thread.sleep(300);
      }
    }
    //release
    TLlamaAMReleaseRequest trelReq = new TLlamaAMReleaseRequest();
    trelReq.setVersion(TLlamaServiceVersion.V1);
    trelReq.setAm_handle(trRes.getAm_handle());
    trelReq.setReservation_id(tresRes.getReservation_id());
    TLlamaAMReleaseResponse trelRes = client.Release(trelReq);
    Assert.assertEquals(TStatusCode.OK, trelRes.getStatus().getStatus_code());
  }


}
