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
import com.cloudera.llama.am.mock.MockLlamaAM;
import com.cloudera.llama.am.server.thrift.LlamaAMThriftServer;
import com.cloudera.llama.am.server.thrift.ServerConfiguration;
import com.cloudera.llama.am.yarn.YarnLlamaAM;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;

public class MiniLlama {

  public static final String MINI_SERVER_CLASS_KEY = 
      "llama.am.server.mini.server.class";

  private static final String START_MINI_YARN = 
      "llama.am.server.mini.start.mini.yarn";

  private static final String MINI_YARN_NODES = 
      "llama.am.server.mini.yarn.nodes";

  private static final String YARN_QUEUES = "llama.am.server.mini.yarn.queues";

  public enum Type {MOCK, YARN}

  private static String toString(List<String> list) {
    StringBuilder sb = new StringBuilder();
    String separator = "";
    for (String s : list) {
      sb.append(separator).append(s);
      separator = ",";
    }
    return sb.toString();
  }

  public static Configuration createMiniConf(Type llamaType,
      List<String> queues, int numberOfNodes, Configuration baseConf) {
    ParamChecker.notNull(llamaType, "llamaType");
    ParamChecker.notNulls(queues, "queues");
    baseConf = (baseConf == null) ? new Configuration(false) : baseConf;
    Configuration conf = new Configuration(baseConf);
    if (llamaType == Type.MOCK) {
      conf.setClass(LlamaAM.CLASS_KEY, MockLlamaAM.class, LlamaAM.class);
      conf.set(LlamaAM.INITIAL_QUEUES_KEY, toString(queues));
      conf.set(MockLlamaAM.QUEUES_KEY, toString(queues));
      List<String> nodes = new ArrayList<String>();
      for (int i = 0; i < numberOfNodes; i++) {
        nodes.add("node-" + i);
      }
      conf.set(MockLlamaAM.NODES_KEY, toString(nodes));
      conf.setIfUnset(MockLlamaAM.EVENTS_MIN_WAIT_KEY, "1000");
      conf.setIfUnset(MockLlamaAM.EVENTS_MAX_WAIT_KEY, "10000");

      conf.setIfUnset(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");

      conf.setBoolean(START_MINI_YARN, false);
    } else {
      conf.setClass(LlamaAM.CLASS_KEY, YarnLlamaAM.class, LlamaAM.class);
      conf.set(LlamaAM.INITIAL_QUEUES_KEY, toString(queues));
      conf.set(YARN_QUEUES, toString(queues));
      conf.setInt(MINI_YARN_NODES, numberOfNodes);

      conf.setIfUnset(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");

      conf.setBoolean(START_MINI_YARN, true);
    }
    return conf;
  }

  private Configuration conf;
  private AbstractServer server;

  public MiniLlama(Configuration conf) {
    ParamChecker.notNull(conf, "conf");
    this.conf = conf;
    Class<? extends AbstractServer> klass = conf.getClass(MINI_SERVER_CLASS_KEY, 
        LlamaAMThriftServer.class, AbstractServer.class);
    server = ReflectionUtils.newInstance(klass, conf);
  }

  public Configuration getConf() {
    return conf;
  }

  public void start() {
    if (conf.getBoolean(START_MINI_YARN, false)) {
      startMiniYarn();
      //TODO seed yarn conf for llama server
    }
    server.start();
  }

  public void stop() {
    server.stop();
    if (conf.getBoolean(START_MINI_YARN, false)) {
      stopMiniYarn();
    }
  }

  public String getAddressHost() {
    return server.getAddressHost();
  }

  public int getAddressPort() {
    return server.getAddressPort();
  }

  private Configuration getMiniYarnConf() {
    //TODO
    return null;
  }

  private void startMiniYarn() {
    //TODO    
  }

  private void stopMiniYarn() {
    //TODO    
  }

}
