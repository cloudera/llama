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
package com.cloudera.llama.am.server;

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
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import java.util.Arrays;

public class TestMiniLlamaWithMock {

  @Test
  public void testMiniLlama() throws Exception {
    Configuration conf = MiniLlama.createMiniConf(MiniLlama.Type.MOCK, 
        Arrays.asList("queue1", "queue2"), Arrays.asList("node1", "node2"));
    MiniLlama server = new MiniLlama(conf);
    try {
      server.start();
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
      Assert.assertEquals(Arrays.asList("node1", "node2"), tgnRes.getNodes());

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
