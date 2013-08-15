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

import com.cloudera.llama.am.LlamaAM;
import com.cloudera.llama.am.impl.ParamChecker;
import com.cloudera.llama.am.mock.MockRMLlamaAMConnector;
import com.cloudera.llama.am.server.thrift.LlamaAMThriftServer;
import com.cloudera.llama.am.server.thrift.NodeMapper;
import com.cloudera.llama.am.server.thrift.ServerConfiguration;
import com.cloudera.llama.am.spi.RMLlamaAMConnector;
import com.cloudera.llama.am.yarn.YarnRMLlamaAMConnector;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.ProxyUsers;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.MiniYARNCluster;
import org.apache.hadoop.yarn.server.nodemanager.NodeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniLlama {
  private static final Logger LOG = LoggerFactory.getLogger(MiniLlama.class);

  public static final String MINI_SERVER_CLASS_KEY = 
      "llama.am.server.mini.server.class";

  private static final String START_MINI_CLUSTER = 
      "llama.am.server.mini.start.mini.cluster";

  private static final String MINI_CLUSTER_NODES_KEY = 
      "llama.am.server.mini.cluster.nodes";

  public static Configuration createMockConf(List<String> queues, 
      List<String> nodes) {
    ParamChecker.notNulls(queues, "queues");
    ParamChecker.notNulls(nodes, "nodes");
    if (nodes.isEmpty()) {
      throw new IllegalArgumentException("nodes cannot be empty");
    }
    Configuration conf = new Configuration(false);
    conf.set(ServerConfiguration.CONFIG_DIR_KEY, "");
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MockRMLlamaAMConnector.class, RMLlamaAMConnector.class);
    conf.setStrings(LlamaAM.INITIAL_QUEUES_KEY, queues.toArray(new String[0]));
    conf.setStrings(MockRMLlamaAMConnector.QUEUES_KEY, queues.toArray(new String[0]));
    conf.setStrings(MockRMLlamaAMConnector.NODES_KEY, nodes.toArray(new String[0]));
    conf.set(MockRMLlamaAMConnector.EVENTS_MIN_WAIT_KEY, "1000");
    conf.set(MockRMLlamaAMConnector.EVENTS_MAX_WAIT_KEY, "10000");
    conf.set(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");
    conf.setBoolean(START_MINI_CLUSTER, false);
    return conf;
  }

  public static Configuration createMiniClusterConf(int nodes) {
    ParamChecker.greaterThan(nodes, 1, "nodes");
    if (nodes > 1) {
      throw new IllegalArgumentException(
          "More than one node is not supported until YARN-1008 is committed");
    }
    Configuration conf = new Configuration(false);
    conf.set(ServerConfiguration.CONFIG_DIR_KEY, "");
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, YarnRMLlamaAMConnector.class, RMLlamaAMConnector.class);
    conf.setInt(MINI_CLUSTER_NODES_KEY, nodes);
    conf.set(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");
    conf.setBoolean(START_MINI_CLUSTER, true);
    return conf;
  }

  private final Configuration conf;
  private final AbstractServer server;
  private List<String> dataNodes;
  private MiniDFSCluster miniHdfs;
  private MiniYARNCluster miniYarn;

  public MiniLlama(Configuration conf) {
    ParamChecker.notNull(conf, "conf");
    Class<? extends AbstractServer> klass = conf.getClass(MINI_SERVER_CLASS_KEY, 
        LlamaAMThriftServer.class, AbstractServer.class);
    server = ReflectionUtils.newInstance(klass, conf);
    this.conf = server.getConf();
  }

  public Configuration getConf() {
    return conf;
  }

  public void start() throws Exception {
    if (conf.getBoolean(START_MINI_CLUSTER, false)) {
      Map<String, String> mapping = startMiniHadoop();
      server.getConf().setClass(ServerConfiguration.NODE_NAME_MAPPING_CLASS_KEY,
          MiniClusterNodeMapper.class, NodeMapper.class);
      MiniClusterNodeMapper.addMapping(getConf(), mapping);
      for (Map.Entry entry : miniYarn.getConfig()) {
        conf.set((String) entry.getKey(), (String)entry.getValue());
      }
      dataNodes = new ArrayList<String>(mapping.keySet());
    } else {
      dataNodes = new ArrayList<String>(
          conf.getStringCollection(MockRMLlamaAMConnector.NODES_KEY));
    }
    dataNodes = Collections.unmodifiableList(dataNodes);
    server.start();
  }

  public void stop() {
    server.stop();
    if (conf.getBoolean(START_MINI_CLUSTER, false)) {
      stopMiniHadoop();
    }
  }

  public String getAddressHost() {
    return server.getAddressHost();
  }

  public int getAddressPort() {
    return server.getAddressPort();
  }

  public List<String> getDataNodes() {
    return dataNodes;
  }
  
  private Map<String, String> startMiniHadoop() throws Exception {
    int clusterNodes = getConf().getInt(MINI_CLUSTER_NODES_KEY, 1);
    if (System.getProperty(MiniDFSCluster.PROP_TEST_BUILD_DATA) == null) {
      String testBuildData = new File("target").getAbsolutePath();
      System.setProperty(MiniDFSCluster.PROP_TEST_BUILD_DATA, testBuildData);
    }

    Configuration conf = new YarnConfiguration();
    String llamaProxyUser = System.getProperty("user.name");
    conf.set("hadoop.security.authentication", "simple");
    conf.set("hadoop.proxyuser." + llamaProxyUser + ".hosts", "*");
    conf.set("hadoop.proxyuser." + llamaProxyUser + ".groups", "*");
    String[] userGroups = new String[]{"g"};
    UserGroupInformation.createUserForTesting(llamaProxyUser, userGroups);

    miniHdfs = new MiniDFSCluster(conf, clusterNodes, true, null);
    conf = miniHdfs.getConfiguration(0);
    miniYarn = new MiniYARNCluster("minillama", clusterNodes, 1, 1);
    //TODO YARN-1008
    conf.setBoolean("TODO.USE.PORT.FOR.NODE.NAME", true);
    miniYarn.init(conf);
    miniYarn.start();
    
    ProxyUsers.refreshSuperUserGroupsConfiguration(conf);

    Map<String, String> mapping = new HashMap<String, String>();
    LOG.info("Nodes:");
    for (int i = 0; i < clusterNodes; i++) {
      DataNode dn = miniHdfs.getDataNodes().get(i);
      String key = dn.getDatanodeId().getXferAddr();
      NodeManager nm = miniYarn.getNodeManager(i);
      NodeId nodeId = nm.getNMContext().getNodeId();
      //TODO YARN-1008
      String value = nodeId.getHost();//+ ":" + nodeId.getPort();
      mapping.put(key,  value);
      LOG.info("  DN: " + key);
    }
    System.out.println();
    return mapping;
  }

  private void stopMiniHadoop() {
    if (miniYarn != null) {
      miniYarn.stop();
      miniYarn = null;
    }
    if (miniHdfs != null) {
      miniHdfs.shutdown();
      miniHdfs = null;
    }
  }

}
