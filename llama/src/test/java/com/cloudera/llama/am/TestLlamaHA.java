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
import com.cloudera.llama.am.mock.MockLlamaAMFlags;
import com.cloudera.llama.am.mock.MockRMConnector;
import com.cloudera.llama.am.spi.RMConnector;
import com.cloudera.llama.server.ServerConfiguration;
import com.cloudera.llama.server.TestAbstractMain;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ha.ClientBaseWithFixes;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class TestLlamaHA extends ClientBaseWithFixes {
  private static final int TIMEOUT_MS =
      HAServerConfiguration.ZK_TIMEOUT_MS_DEFAULT;
  private Configuration conf;

  @Before
  public void setup() {
    conf = new Configuration(false);
    conf.set(
        ServerConfiguration.CONFIG_DIR_KEY, TestAbstractMain.createTestDir());

    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MockRMConnector.class,
        RMConnector.class);
    conf.set(LlamaAM.CORE_QUEUES_KEY, "root.q1,root.q2");
    conf.set(MockRMConnector.QUEUES_KEY, "root.q1,root.q2");
    String nodesKey = "" +
        MockLlamaAMFlags.ALLOCATE + "n1";
    conf.set(MockRMConnector.NODES_KEY, nodesKey);
    conf.setInt(MockRMConnector.EVENTS_MIN_WAIT_KEY, 5);
    conf.setInt(MockRMConnector.EVENTS_MAX_WAIT_KEY, 10);

    ServerConfiguration sConf = new AMServerConfiguration(conf);
    conf.set(sConf.getPropertyName(ServerConfiguration.SERVER_ADDRESS_KEY),
        "localhost:0");
    conf.set(sConf.getPropertyName(ServerConfiguration.SERVER_ADMIN_ADDRESS_KEY),
        "localhost:0");
    conf.set(sConf.getPropertyName(ServerConfiguration.HTTP_ADDRESS_KEY),
        "localhost:0");

    conf.setBoolean(HAServerConfiguration.HA_ENABLED, true);
    conf.set(HAServerConfiguration.ZK_QUORUM, hostPort);
  }

  /**
   * Wait until TIMEOUT_MS for the passed LlamaHAServer to become Active.
   * Fail the test if it doesn't.
   */
  private void waitAndCheckActive(LlamaHAServer server)
      throws InterruptedException, IOException {
    // Wait for server to become Active
    for (int i = 0; i < TIMEOUT_MS / 100; i++) {
      if (server.isActive()) {
        break;
      }
      Thread.sleep(100);
    }
    assertTrue(server.isActive());
    TestLlamaAMThriftServer.checkLlamaStarted(server);
  }

  @Test
  public void testSingleLlamaHA() throws InterruptedException, IOException {
    LlamaHAServer server = new LlamaHAServer();
    try {
      server.setConf(conf);
      server.start();
      waitAndCheckActive(server);
    } finally {
      server.stop();
    }
  }

  @Test
  public void testSingleLlamaHAMultipleTransitions() throws Exception {
    LlamaHAServer server = new LlamaHAServer();
    try {
      server.setConf(conf);

      server.start();
      waitAndCheckActive(server);

      for (int i = 0; i < 4; i++) {
        server.foregoActive(0);
        waitAndCheckActive(server);
      }
    } finally {
      server.stop();
    }
  }

  @Test
  public void testTwoLlamasHA() throws Exception {
    LlamaHAServer server1 = new LlamaHAServer();
    LlamaHAServer server2 = new LlamaHAServer();

    try {
      server1.setConf(conf);
      server2.setConf(conf);

      server1.start();
      waitAndCheckActive(server1);
      server2.start();

      for (int i = 0; i < 3; i++) {
        server1.foregoActive(100);
        waitAndCheckActive(server2);

        server2.foregoActive(100);
        waitAndCheckActive(server1);
      }
    } finally {
      server1.stop();
      server2.stop();
    }
  }

  @Test (timeout = 300000)
  public void testFencing() throws Exception {
    LlamaHAServer server1 = new LlamaHAServer();
    LlamaHAServer server2 = new LlamaHAServer();

    try {
      server1.setConf(conf);
      server2.setConf(conf);

      server1.start();
      waitAndCheckActive(server1);
      boolean server1Fenced = false;

      server2.start();
      server2.fencer.fenceOthers();

      for (int i = 0; i < TIMEOUT_MS/100; i++) {
        if (!server1.isActive()) {
          server1Fenced = true;
          break;
        }
        Thread.sleep(100);
      }
      assertTrue("Server 1 should be fenced and standby", server1Fenced);
    } finally {
      server1.stop();
      server2.stop();
    }
  }
}
