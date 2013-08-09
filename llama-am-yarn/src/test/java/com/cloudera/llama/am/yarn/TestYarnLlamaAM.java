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

import com.cloudera.llama.am.LlamaAM;
import com.cloudera.llama.am.LlamaAMEvent;
import com.cloudera.llama.am.LlamaAMListener;
import com.cloudera.llama.am.Reservation;
import com.cloudera.llama.am.Resource;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.MiniYARNCluster;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestYarnLlamaAM {

  public static class MyListener implements LlamaAMListener {
    public List<LlamaAMEvent> events = Collections.synchronizedList(new
        ArrayList<LlamaAMEvent>());

    @Override
    public void handle(LlamaAMEvent event) {
      events.add(event);
    }
  }

  private static MiniYARNCluster miniYarn;

  @BeforeClass
  public static void startYarn() throws Exception {
    miniYarn = new MiniYARNCluster("minillama", 1, 1, 1);
    miniYarn.init(new YarnConfiguration());
    miniYarn.start();    
  }
  
  @AfterClass
  public static void stopYarn() {
    miniYarn.stop();
  }
  
  protected Configuration getConfiguration() {
    Configuration conf = new Configuration(false);
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "default");
    conf.set(LlamaAM.CLASS_KEY, YarnLlamaAM.class.getName());
    for (Map.Entry entry : miniYarn.getConfig()) {
      conf.set((String) entry.getKey(), (String)entry.getValue());
    }
    return conf;
  }


  @Test
  public void testReserve() throws Exception {
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    MyListener listener = new MyListener();
    try {
      llama.start();
      llama.addListener(listener);
      List<String> nodes = llama.getNodes();
      Assert.assertFalse(nodes.isEmpty());
      Resource a1 = new Resource(UUID.randomUUID(),nodes.get(0),
          Resource.LocationEnforcement.MUST, 1, 1024);
      llama.reserve(new Reservation(UUID.randomUUID(), "default",
          Arrays.asList(a1), true));
      while (listener.events.isEmpty());
      System.out.println(listener.events);
    } finally {
      llama.stop();
    }
  }

}
