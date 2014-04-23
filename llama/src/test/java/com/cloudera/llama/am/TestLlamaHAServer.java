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

import com.cloudera.llama.server.ServerConfiguration;
import com.cloudera.llama.server.TestAbstractMain;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ha.ServiceFailedException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestLlamaHAServer {
  private LlamaHAServer server;

  @Before
  public void setup() {
    Configuration conf = new Configuration(false);
    conf.set(
        ServerConfiguration.CONFIG_DIR_KEY, TestAbstractMain.createTestDir());

    ServerConfiguration sConf = new AMServerConfiguration(conf);
    conf.set(sConf.getPropertyName(ServerConfiguration.SERVER_ADDRESS_KEY),
        "localhost:0");
    conf.set(sConf.getPropertyName(ServerConfiguration.SERVER_ADMIN_ADDRESS_KEY),
        "localhost:0");
    conf.set(sConf.getPropertyName(ServerConfiguration.HTTP_ADDRESS_KEY),
        "localhost:0");

    conf.setBoolean(HAServerConfiguration.HA_ENABLED, true);
    server = new LlamaHAServer();
    server.setConf(conf);
    server.start();
  }

  @Test
  public void testHAEnabledStartsInStandby() {
    assertFalse("Server is already active", server.isActive());
  }

  @Test
  public void testBecomeActive() throws ServiceFailedException {
    server.becomeActive();
    assertTrue("Server still not active", server.isActive());
  }

  @Test
  public void testBecomeStandby() throws ServiceFailedException {
    server.becomeActive();
    server.becomeStandby();
    assertFalse("Server still active", server.isActive());
  }

  @Test
  public void testForegoActive() throws ServiceFailedException {
    server.becomeActive();
    server.foregoActive(0);
    assertFalse("Server still active", server.isActive());
  }

  @Test
  public void testMultipleBecomeActive() throws ServiceFailedException {
    server.becomeActive();
    server.becomeActive();
    assertTrue("Server should remain active", server.isActive());
  }

  @Test
  public void testMultipleBecomeStandby() throws ServiceFailedException {
    server.becomeActive();
    server.becomeStandby();
    server.becomeStandby();
    assertFalse("Server should remain Standby", server.isActive());
  }
}
