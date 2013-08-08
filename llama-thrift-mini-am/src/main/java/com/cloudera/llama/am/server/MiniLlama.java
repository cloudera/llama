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

  public static Configuration createMiniConf(Type llamaType,
      List<String> queues, List<String> nodes) {
    ParamChecker.notNull(llamaType, "llamaType");
    ParamChecker.notNulls(queues, "queues");
    Configuration conf = new Configuration(false);
    conf.set(ServerConfiguration.CONFIG_DIR_KEY, "");
    if (llamaType == Type.MOCK) {
      conf.setClass(LlamaAM.CLASS_KEY, MockLlamaAM.class, LlamaAM.class);
      conf.setStrings(LlamaAM.INITIAL_QUEUES_KEY, queues.toArray(new String[0]));
      conf.setStrings(MockLlamaAM.QUEUES_KEY, queues.toArray(new String[0]));
      conf.setStrings(MockLlamaAM.NODES_KEY, nodes.toArray(new String[0]));
      conf.set(MockLlamaAM.EVENTS_MIN_WAIT_KEY, "1000");
      conf.set(MockLlamaAM.EVENTS_MAX_WAIT_KEY, "10000");

      conf.set(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");

      conf.setBoolean(START_MINI_YARN, false);
    } else {
      conf.setClass(LlamaAM.CLASS_KEY, YarnLlamaAM.class, LlamaAM.class);
      conf.setStrings(LlamaAM.INITIAL_QUEUES_KEY, queues.toArray(new String[0]));
      conf.setStrings(MockLlamaAM.QUEUES_KEY, queues.toArray(new String[0]));
      conf.setInt(MINI_YARN_NODES, nodes.size());

      conf.set(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");

      conf.setBoolean(START_MINI_YARN, true);
    }
    return conf;
  }

  private final Configuration conf;
  private final AbstractServer server;

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
