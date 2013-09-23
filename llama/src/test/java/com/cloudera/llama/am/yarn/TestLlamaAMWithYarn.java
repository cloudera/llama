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
package com.cloudera.llama.am.yarn;

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.api.Resource;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.ProxyUsers;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.MiniYARNCluster;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TestLlamaAMWithYarn {

  public static class MyListener implements LlamaAMListener {
    public List<LlamaAMEvent> events = Collections.synchronizedList(new
        ArrayList<LlamaAMEvent>());

    @Override
    public void handle(LlamaAMEvent event) {
      events.add(event);
    }
  }

  private static MiniYARNCluster miniYarn;

  private Configuration createMiniYarnConfig(boolean usePortInName)
      throws Exception {
    Configuration conf = new YarnConfiguration();
    //scheduler config
    URL url = Thread.currentThread().getContextClassLoader().
        getResource("fair-scheduler.xml");
    if (url == null) {
      throw new RuntimeException("'fair-scheduler.xml' file not " +
          "found in classpath");
    }
    String fsallocationFile = url.toExternalForm();
    if (!fsallocationFile.startsWith("file:")) {
      throw new RuntimeException("File 'fair-scheduler.xml' is in " +
          "a JAR, it should be in a directory");
    }
    fsallocationFile = fsallocationFile.substring("file:".length());
    conf.set("yarn.scheduler.fair.allocation.file", fsallocationFile);

    //proxy user config
    String llamaProxyUser = System.getProperty("user.name");
    conf.set("hadoop.security.authentication", "simple");
    conf.set("hadoop.proxyuser." + llamaProxyUser + ".hosts", "*");
    conf.set("hadoop.proxyuser." + llamaProxyUser + ".groups", "*");
    String[] userGroups = new String[]{"g"};
    UserGroupInformation.createUserForTesting(llamaProxyUser, userGroups);
    conf.setBoolean(YarnConfiguration.RM_SCHEDULER_INCLUDE_PORT_IN_NODE_NAME,
        usePortInName);
    return conf;
  }

  private void startYarn(Configuration conf, int nodeManagers) throws Exception {
    miniYarn = new MiniYARNCluster("minillama", nodeManagers, 1, 1);
    miniYarn.init(conf);
    miniYarn.start();
    ProxyUsers.refreshSuperUserGroupsConfiguration(conf);
  }

  private void startYarn(Configuration conf) throws Exception {
    startYarn(conf, 1);
  }

  private void stopYarn() {
    if (miniYarn != null) {
      miniYarn.stop();
      miniYarn = null;
    }
  }

  private void restartMiniYarn() throws Exception {
    Configuration conf = miniYarn.getConfig();
    conf.setBoolean(YarnConfiguration.YARN_MINICLUSTER_FIXED_PORTS, true);
    stopYarn();
    startYarn(conf);
  }

  protected static Configuration getLlamaConfiguration() {
    Configuration conf = new Configuration(false);
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "queue1,queue2");
    conf.set(LlamaAM.RM_CONNECTOR_CLASS_KEY, YarnRMLlamaAMConnector.class
        .getName());
    conf.setInt(YarnRMLlamaAMConnector.HEARTBEAT_INTERVAL_KEY, 50);
    for (Map.Entry entry : miniYarn.getConfig()) {
      conf.set((String) entry.getKey(), (String) entry.getValue());
    }
    return conf;
  }

  @Test
  public void testGetNodes() throws Exception {
    try {
      startYarn(createMiniYarnConfig(true));
      Configuration conf = getLlamaConfiguration();
      conf.unset(LlamaAM.INITIAL_QUEUES_KEY);
      LlamaAM llama = LlamaAM.create(conf);
      try {
        llama.start();
        List<String> nodes = llama.getNodes();
        Assert.assertFalse(nodes.isEmpty());
      } finally {
        llama.stop();
      }
    } finally {
      stopYarn();
    }
  }


  private void testReserve(boolean usePortInName) throws Exception {
    try {
      startYarn(createMiniYarnConfig(usePortInName));
      LlamaAM llama = LlamaAM.create(getLlamaConfiguration());
      MyListener listener = new MyListener();
      try {
        llama.start();
        llama.addListener(listener);
        List<String> nodes = llama.getNodes();
        Assert.assertFalse(nodes.isEmpty());
        Resource a1 = new Resource(UUID.randomUUID(), nodes.get(0),
            Resource.LocationEnforcement.MUST, 1, 1024);
        llama.reserve(new Reservation(UUID.randomUUID(), "queue1",
            Arrays.asList(a1), true));
        while (listener.events.isEmpty()) {
          Thread.sleep(100);
        }
      } finally {
        llama.stop();
      }
    } finally {
      stopYarn();
    }
  }

  @Test(timeout = 60000)
  public void testReserveWithPortInName() throws Exception {
    testReserve(true);
  }

  @Test(timeout = 60000)
  public void testReserveWithoutPortInName() throws Exception {
    testReserve(false);
  }

  @Test(timeout = 600000)
  public void testYarnRestart() throws Exception {
    try {
      startYarn(createMiniYarnConfig(false));
      LlamaAM llama = LlamaAM.create(getLlamaConfiguration());
      MyListener listener = new MyListener();
      try {
        llama.start();
        llama.addListener(listener);
        List<String> nodes = llama.getNodes();
        Assert.assertFalse(nodes.isEmpty());
        Resource a1 = new Resource(UUID.randomUUID(), nodes.get(0),
            Resource.LocationEnforcement.MUST, 1, 1024);
        llama.reserve(new Reservation(UUID.randomUUID(), "queue1",
            Arrays.asList(a1), true));
        while (listener.events.isEmpty()) {
          Thread.sleep(100);
        }
        restartMiniYarn();
        listener.events.clear();
        llama.reserve(new Reservation(UUID.randomUUID(), "queue1",
            Arrays.asList(a1), true));
        while (listener.events.isEmpty()) {
          Thread.sleep(100);
        }
        LlamaAMEvent event = listener.events.get(0);
        Assert.assertEquals(1, event.getRejectedReservationIds().size());
      } finally {
        llama.stop();
      }
    } finally {
      stopYarn();
    }
  }

  @Test
  public void testReserveAllEnforcements() throws Exception {
    try {
      startYarn(createMiniYarnConfig(false));
      LlamaAM llama = LlamaAM.create(getLlamaConfiguration());
      MyListener listener = new MyListener();
      try {
        llama.start();
        llama.addListener(listener);
        List<String> nodes = llama.getNodes();
        Assert.assertFalse(nodes.isEmpty());
        Resource r = new Resource(UUID.randomUUID(), nodes.get(0),
            Resource.LocationEnforcement.MUST, 1, 1024);
        UUID pr1 = llama.reserve(new Reservation(UUID.randomUUID(), "queue1",
            Arrays.asList(r), true));
        r = new Resource(UUID.randomUUID(), nodes.get(0),
            Resource.LocationEnforcement.PREFERRED, 1, 1024);
        UUID pr2 = llama.reserve(new Reservation(UUID.randomUUID(), "queue1",
            Arrays.asList(r), true));
        r = new Resource(UUID.randomUUID(), nodes.get(0),
            Resource.LocationEnforcement.DONT_CARE, 1, 1024);
        UUID pr3 = llama.reserve(new Reservation(UUID.randomUUID(), "queue1",
            Arrays.asList(r), true));
        while (listener.events.size() < 3) {
          Thread.sleep(100);
        }
        
        Set<UUID> expected = new HashSet<UUID>();
        expected.add(pr1);
        expected.add(pr2);
        expected.add(pr3);
        
        Set<UUID> got = new HashSet<UUID>();
        for (LlamaAMEvent event : listener.events) {
          got.addAll(event.getAllocatedReservationIds());
        }
        Assert.assertEquals(expected, got);
      } finally {
        llama.stop();
      }
    } finally {
      stopYarn();
    }
  }

  @Test
  public void testClusterNMChanges() throws Exception {
    try {
      Configuration conf = createMiniYarnConfig(true);
      conf.setInt(YarnConfiguration.RM_NM_EXPIRY_INTERVAL_MS, 1000);
      startYarn(conf, 2);
      LlamaAM llama = LlamaAM.create(getLlamaConfiguration());
      MyListener listener = new MyListener();
      try {
        llama.start();
        llama.addListener(listener);
        List<String> nodes = llama.getNodes();
        Assert.assertEquals(2, nodes.size());
        miniYarn.getNodeManager(0).stop();
        long startTime = System.currentTimeMillis();
        while (llama.getNodes().size() != 1
            && System.currentTimeMillis() - startTime < 10000) {
          Thread.sleep(100);
        }
        nodes = llama.getNodes();
        Assert.assertEquals(1, nodes.size());
      } finally {
        llama.stop();
      }
    } finally {
      stopYarn();
    }
  }


}
