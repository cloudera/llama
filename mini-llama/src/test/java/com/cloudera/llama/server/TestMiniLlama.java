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
import com.cloudera.llama.thrift.TLlamaAMRegisterRequest;
import com.cloudera.llama.thrift.TLlamaAMRegisterResponse;
import com.cloudera.llama.thrift.TLlamaAMUnregisterRequest;
import com.cloudera.llama.thrift.TLlamaAMUnregisterResponse;
import com.cloudera.llama.thrift.TLlamaServiceVersion;
import com.cloudera.llama.thrift.TNetworkAddress;
import com.cloudera.llama.thrift.TStatusCode;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.UUID;

public class TestMiniLlama {

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

  private void testMiniLlamaWithHadoopMiniCluster(boolean writeHdfsConf) 
      throws Exception {
    URL url = Thread.currentThread().getContextClassLoader().getResource(
        "fair-scheduler.xml");
    if (url == null) {
      throw new RuntimeException(
          "Missing 'fair-scheduler.xml' file in classpath");
    }
    String fsallocationFile = url.toExternalForm();
    if (!fsallocationFile.startsWith("file:")) {
      throw new RuntimeException("File 'fair-scheduler.xml' is in " +
          "a JAR, it should be in a directory");
    }
    fsallocationFile = fsallocationFile.substring("file:".length());
    Configuration conf = MiniLlama.createMiniClusterConf(2);
    conf.set("yarn.scheduler.fair.allocation.file", fsallocationFile);
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "default");
    testMiniLlama(conf, writeHdfsConf);
  }

  private void testMiniLlama(Configuration conf, boolean writeHdfsConf) 
      throws Exception {
    File confFile = null;
    MiniLlama server = new MiniLlama(conf);
    try {
      Assert.assertNotNull(server.getConf().get(LlamaAM.INITIAL_QUEUES_KEY));
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
      trReq.setClient_id("c1");
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
      Assert.assertEquals(new HashSet<String>(server.getDataNodes()),
          new HashSet<String>(tgnRes.getNodes()));

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
    }
  }

}
