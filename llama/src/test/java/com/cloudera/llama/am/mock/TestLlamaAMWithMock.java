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
package com.cloudera.llama.am.mock;

import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.mock.mock.MockLlamaAMFlags;
import com.cloudera.llama.am.mock.mock.MockRMLlamaAMConnector;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TestLlamaAMWithMock {

  public static class MockListener implements LlamaAMListener {
    public List<LlamaAMEvent> events = Collections.synchronizedList(
        new ArrayList<LlamaAMEvent>());

    @Override
    public void handle(LlamaAMEvent event) {
      events.add(event);
    }
  }

  protected Configuration getConfiguration() {
    Configuration conf = new Configuration(false);
    conf.set("llama.am.mock.queues", "q1,q2");
    conf.set("llama.am.mock.events.min.wait.ms", "10");
    conf.set("llama.am.mock.events.max.wait.ms", "10");
    conf.set("llama.am.mock.nodes", "h0,h1,h2,h3");
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "q1");
    conf.set(LlamaAM.RM_CONNECTOR_CLASS_KEY, MockRMLlamaAMConnector.class.getName());
    return conf;
  }

  @Test
  public void testMocks() throws Exception {
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    MockListener listener = new MockListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID c1 = UUID.randomUUID();
      UUID c2 = UUID.randomUUID();
      UUID c3 = UUID.randomUUID();
      UUID c4 = UUID.randomUUID();
      Resource a1 = new Resource(c1, MockLlamaAMFlags.ALLOCATE + "h0", 
          Resource.LocationEnforcement.DONT_CARE, 1, 1);
      Resource a2 = new Resource(c2, MockLlamaAMFlags.REJECT +"h1", 
          Resource.LocationEnforcement.DONT_CARE, 1, 1);
      Resource a3 = new Resource(c3, MockLlamaAMFlags.PREEMPT +"h2", 
          Resource.LocationEnforcement.DONT_CARE, 1, 1);
      Resource a4 = new Resource(c4, MockLlamaAMFlags.LOSE +"h3", 
          Resource.LocationEnforcement.DONT_CARE, 1, 1);
      llama.reserve(new Reservation(UUID.randomUUID(), "q1", Arrays.asList(a1), 
          false));
      llama.reserve(new Reservation(UUID.randomUUID(), "q1", Arrays.asList(a2), 
          false));
      llama.reserve(new Reservation(UUID.randomUUID(), "q1", Arrays.asList(a3), 
          false));
      llama.reserve(new Reservation(UUID.randomUUID(), "q1", Arrays.asList(a4), 
          false));
      Thread.sleep(100);
      Assert.assertEquals(6, listener.events.size());
      Set<UUID> allocated = new HashSet<UUID>();
      allocated.add(c1);
      allocated.add(c3);
      allocated.add(c4);
      Set<UUID> rejected = new HashSet<UUID>();
      rejected.add(c2);
      Set<UUID> lost = new HashSet<UUID>();
      lost.add(c4);
      Set<UUID> preempted = new HashSet<UUID>();
      preempted.add(c3);
      for (LlamaAMEvent event : listener.events) {
        if (!event.getAllocatedResources().isEmpty()) {
          allocated.remove(event.getAllocatedResources().get(0)
              .getClientResourceId());
        }
        if (!event.getRejectedClientResourcesIds().isEmpty()) {
          rejected.remove(event.getRejectedClientResourcesIds().get(0));
        }
        if (!event.getLostClientResourcesIds().isEmpty()) {
          lost.remove(event.getLostClientResourcesIds().get(0));
        }
        if (!event.getPreemptedClientResourceIds().isEmpty()) {
          preempted.remove(event.getPreemptedClientResourceIds().get(0));
        }
      }
      Set<UUID> remaining = new HashSet<UUID>();
      remaining.addAll(allocated);
      remaining.addAll(rejected);
      remaining.addAll(lost);
      remaining.addAll(preempted);
      Assert.assertTrue(remaining.isEmpty());

      listener.events.clear();
      UUID c5 = UUID.randomUUID();
      Resource a5 = new Resource(c5, MockLlamaAMFlags.ALLOCATE +"XX", 
          Resource.LocationEnforcement.DONT_CARE, 1, 1);
      llama.reserve(new Reservation(UUID.randomUUID(), "q1", Arrays.asList(a5), 
          true));
      Thread.sleep(100);
      Assert.assertEquals(1, listener.events.size());
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedClientResourcesIds().size());
    } finally {
      llama.stop();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidQueue() throws Exception {
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    try {
      llama.start();
      UUID c1 = UUID.randomUUID();
      Resource a1 = new Resource(c1, MockLlamaAMFlags.ALLOCATE + "h0",
          Resource.LocationEnforcement.DONT_CARE, 1, 1);
      llama.reserve(new Reservation(UUID.randomUUID(), "invalid-q", 
          Arrays.asList(a1), false));
    } finally {
      llama.stop();
    }
  }

  private boolean hasAllStatus(List<LlamaAMEvent> events) {
    events = new ArrayList<LlamaAMEvent>(events);
    boolean lost = false;
    boolean rejected = false;
    boolean preempted = false;
    boolean allocated = false;
    for (LlamaAMEvent event: events) {
      if (!event.getLostClientResourcesIds().isEmpty()) {
        lost = true;
      }
      if (!event.getRejectedClientResourcesIds().isEmpty()) {
        rejected = true;
      }
      if (!event.getPreemptedClientResourceIds().isEmpty()) {
        preempted = true;
      }
      if (!event.getAllocatedResources().isEmpty()) {
        allocated = true;
      }
    }
    return lost && rejected && preempted && allocated;
  }
  
  @Test
  public void testRandom() throws Exception {
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    MockListener listener = new MockListener();
    try {
      llama.start();
      llama.addListener(listener);
      while (!hasAllStatus(listener.events)) {
        Resource a1 = new Resource(UUID.randomUUID(),"h0",
            Resource.LocationEnforcement.DONT_CARE, 1, 1);
        llama.reserve(new Reservation(UUID.randomUUID(), "q1",
            Arrays.asList(a1), false));        
      }
    } finally {
      llama.stop();
    }
  }

}
