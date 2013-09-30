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
import com.cloudera.llama.am.impl.ParamChecker;
import com.cloudera.llama.am.yarn.YarnRMLlamaAMConnector;
import com.cloudera.llama.server.AbstractServer;
import com.cloudera.llama.server.NodeMapper;
import com.cloudera.llama.server.ServerConfiguration;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniLlama {

  private static final String NODES_OPT = "hadoopnodes";
  private static final String NOFORMAT_OPT = "noformat";
  private static final String WRITE_HDFS_CONFIG_OPT = "writehdfsconf";

  public static void main(String[] args) throws Exception {
    CommandLine cli = parseArguments(args);
    if (cli == null) {
      return;
    }
    System.setProperty("log4j.configuration", "minillama-log4j.properties");
    Configuration conf = new Configuration(false);
    conf.addResource("llama-site.xml");
    int nodes = intArgument(cli, NODES_OPT, 1);
    conf = createMiniClusterConf(conf, nodes);
    final MiniLlama llama = new MiniLlama(conf);
    llama.skipDfsFormat(cli.hasOption(NOFORMAT_OPT));
    if (cli.hasOption(WRITE_HDFS_CONFIG_OPT)) {
      llama.setWriteHadoopConfig(cli.getOptionValue(WRITE_HDFS_CONFIG_OPT));
    }
    llama.start();
    LOG.info("**************************************************************"
        + "*******************************************************");
    LOG.info("Mini Llama running with HDFS/Yarn minicluster with {} nodes, " +
        "HDFS URI: {} Llama URI: {}", nodes,
        new YarnConfiguration().get("fs.defaultFS"),
        llama.getAddressHost() + ":" + llama.getAddressPort());
    LOG.info("*************************************************************" +
        "********************************************************");
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        llama.stop();
      }
    });
    synchronized (MiniLlama.class) {
      MiniLlama.class.wait();
    }
  }

  /**
   * Creates configuration options object.
   */
  @SuppressWarnings("static-access")
  static Options makeOptions() {
    Options options = new Options();
    options
        .addOption(NODES_OPT, true, "How many nodes to use (default 1)")
        .addOption(NOFORMAT_OPT, false, "Do not format the DFS (default false)")
        .addOption(WRITE_HDFS_CONFIG_OPT, true, 
            "Save Hadoop configuration to this XML file.")
        .addOption(
            OptionBuilder.withDescription("Prints option help.").create("help"));
    return options;
  }

  /**
   * Parses arguments.
   *
   * @param args
   *          Command-line arguments.
   * @return a the parsed args on successful parse; null to indicate that the
   *         program should exit.
   */
  static CommandLine parseArguments(String[] args) {
    Options options = makeOptions();
    CommandLine cli;
    try {
      CommandLineParser parser = new GnuParser();
      cli = parser.parse(options, args);
    } catch (ParseException e) {
      LOG.warn("options parsing failed:  " + e.getMessage());
      new HelpFormatter().printHelp("...", options);
      return null;
    }

    if (cli.hasOption("help")) {
      new HelpFormatter().printHelp("...", options);
      return null;
    }
    if (cli.getArgs().length > 0) {
      //TODO: check this with casey
      for (String arg : cli.getArgs()) {
        System.err.println("Unrecognized option: " + arg);
        new HelpFormatter().printHelp("...", options);
        return null;
      }
    }
    return cli;
  }

  /**
   * Extracts an integer argument with specified default value.
   */
  static int intArgument(CommandLine cli, String argName, int default_) {
    String o = cli.getOptionValue(argName);
    if (o == null) {
      return default_;
    } else {
      return Integer.parseInt(o);
    }
  }

  private static ServerConfiguration S_CONF = new AMServerConfiguration(
      new Configuration(false));

  public static Configuration createMiniClusterConf(Configuration conf,
      int nodes) {
    ParamChecker.notNull(conf, "conf");
    ParamChecker.greaterThan(nodes, 0, "nodes");
    conf.set(ServerConfiguration.CONFIG_DIR_KEY, "");
    conf.setIfUnset(LlamaAM.RM_CONNECTOR_CLASS_KEY, YarnRMLlamaAMConnector
        .class.getName());
    conf.setInt(MINI_CLUSTER_NODES_KEY, nodes);
    conf.setIfUnset(S_CONF.getPropertyName(
        ServerConfiguration.SERVER_ADDRESS_KEY), "localhost:0");
    return conf;
  }

  public static Configuration createMiniClusterConf(int nodes) {
    return createMiniClusterConf(new Configuration(false), nodes);
  }

  private static final Logger LOG = LoggerFactory.getLogger(MiniLlama.class);

  public static final String MINI_SERVER_CLASS_KEY = 
      "llama.am.server.mini.server.class";

  private static final String MINI_CLUSTER_NODES_KEY =
      "llama.am.server.mini.cluster.nodes";

  private final Configuration conf;
  private boolean skipDfsFormat = false;
  private String writeHdfsConfig = null;
  private final AbstractServer server;
  private List<String> dataNodes;
  private MiniDFSCluster miniHdfs;
  private Configuration miniHdfsConf;
  private MiniYARNCluster miniYarn;

  public MiniLlama(Configuration conf) {
    ParamChecker.notNull(conf, "conf");
    Class<? extends AbstractServer> klass = conf.getClass(MINI_SERVER_CLASS_KEY,
        LlamaAMServer.class, AbstractServer.class);
    server = ReflectionUtils.newInstance(klass, conf);
    this.conf = server.getConf();
  }

  public Configuration getConf() {
    return conf;
  }

  public void skipDfsFormat(boolean skipDfsFormat) {
    this.skipDfsFormat = skipDfsFormat;
  }

  public void setWriteHadoopConfig(String writeHdfsConfig) {
    this.writeHdfsConfig = writeHdfsConfig;
  }
  
  public void start() throws Exception {
    Map<String, String> mapping = startMiniHadoop();
    server.getConf().setClass(S_CONF.getPropertyName(
        ServerConfiguration.NODE_NAME_MAPPING_CLASS_KEY),
        MiniClusterNodeMapper.class, NodeMapper.class);
    MiniClusterNodeMapper.addMapping(getConf(), mapping);
    for (Map.Entry entry : miniYarn.getConfig()) {
      conf.set((String) entry.getKey(), (String) entry.getValue());
    }
    if (writeHdfsConfig != null) {
      FileOutputStream fos = new FileOutputStream(new File(writeHdfsConfig));
      miniHdfsConf.writeXml(fos);
      fos.close();
    }
    dataNodes = new ArrayList<String>(mapping.keySet());
    dataNodes = Collections.unmodifiableList(dataNodes);
    server.start();
  }

  public void stop() {
    server.stop();
    stopMiniHadoop();
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

    int hdfsPort = 0;
    String fsUri = conf.get("fs.defaultFS");
    if (fsUri != null && !fsUri.equals("file:///")) {
      int i = fsUri.lastIndexOf(":");
      if (i > -1) {
        try {
          hdfsPort = Integer.parseInt(fsUri.substring(i + 1));
        } catch (Exception ex) {
          throw new RuntimeException("Could not parse port from Hadoop's " +
              "'fs.defaultFS property: " + fsUri);
        }
      }
    }
    miniHdfs = new MiniDFSCluster(hdfsPort, conf, clusterNodes, !skipDfsFormat,
        true, null, null);
    miniHdfs.waitActive();
    conf = miniHdfs.getConfiguration(0);
    miniHdfsConf = conf;
    miniYarn = new MiniYARNCluster("minillama", clusterNodes, 1, 1);
    conf.setBoolean(YarnConfiguration.RM_SCHEDULER_INCLUDE_PORT_IN_NODE_NAME,
        true);
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
      String value = nodeId.getHost() + ":" + nodeId.getPort();
      mapping.put(key, value);
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
