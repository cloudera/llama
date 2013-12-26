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
package com.cloudera.llama.am.cache;

import com.cloudera.llama.am.cache.CacheRMConnector;
import com.cloudera.llama.am.cache.ResourceCache;
import com.cloudera.llama.am.impl.PlacedResourceImpl;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.am.api.RMResource;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.TestUtils;
import com.cloudera.llama.am.spi.RMConnector;
import com.cloudera.llama.am.spi.RMEvent;
import com.cloudera.llama.am.spi.RMListener;
import com.cloudera.llama.util.Clock;
import com.cloudera.llama.util.ManualClock;
import com.cloudera.llama.util.UUID;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestCacheRMConnector {
  private ManualClock manualClock = new ManualClock();

  @Before
  public void setup() {
    Clock.setClock(manualClock);
  }

  @After
  public void destroy() {
    Clock.setClock(Clock.SYSTEM);
  }

  private static class MyRMLlamaConnector implements RMConnector {
    private Set<String> invoked = new HashSet<String>();

    @Override
    public void setLlamaAMCallback(RMListener callback) {
      invoked.add("setLlamaAMCallback");
    }

    @Override
    public void start() throws LlamaException {
      invoked.add("start");
    }

    @Override
    public void stop() {
      invoked.add("stop");
    }

    @Override
    public void register(String queue) throws LlamaException {
      invoked.add("register");
    }

    @Override
    public void unregister() {
      invoked.add("unregister");
    }

    @Override
    public List<String> getNodes() throws LlamaException {
      invoked.add("getNodes");
      return null;
    }

    @Override
    public void reserve(Collection<RMResource> resources)
        throws LlamaException {
      invoked.add("reserve");
    }

    @Override
    public void release(Collection<RMResource> resources, boolean doNotCache)
        throws LlamaException {
      invoked.add("release");
    }

    @Override
    public boolean reassignResource(Object rmResourceId, UUID resourceId) {
      invoked.add("reassignResource");
      return true;
    }

    public void emptyCache() throws LlamaException {
    }

  }

  @Test
  public void testDelegation() throws Exception {
    Set<String> expected = new HashSet<String>();
    expected.add("setLlamaAMCallback");

    MyRMLlamaConnector connector = new MyRMLlamaConnector();

    CacheRMConnector cache = new CacheRMConnector(
        new Configuration(false), connector);

    Assert.assertEquals(expected, connector.invoked);

    expected.add("start");
    expected.add("register");
    expected.add("getNodes");

    cache.setLlamaAMCallback(new RMListener() {
      @Override
      public void stoppedByRM() {
      }

      @Override
      public void onEvent(List<RMEvent> events) {
      }
    });
    cache.start();
    cache.getNodes();
    cache.register("q");
    cache.reassignResource("rm0", UUID.randomUUID());

    Assert.assertEquals(expected, connector.invoked);

    PlacedResourceImpl pr1 = TestUtils.createPlacedResourceImpl("l1",
        Resource.Locality.MUST, 1, 1024);


    cache.reserve(Arrays.asList((RMResource)pr1));
    pr1.setAllocationInfo("'l1", 1, 1024);
    pr1.setRmResourceId("rm1");

    expected.add("reserve");
    Assert.assertEquals(expected, connector.invoked);

    cache.release(Arrays.asList((RMResource)pr1), false);

    expected.add("reassignResource");
    Assert.assertEquals(expected, connector.invoked);

    cache.release(Arrays.asList((RMResource) pr1), true);

    expected.add("release");
    Assert.assertEquals(expected, connector.invoked);

    expected.add("unregister");
    expected.add("stop");

    cache.unregister();
    cache.stop();
    Assert.assertEquals(expected, connector.invoked);
  }

  @Test
  public void testCached() throws Exception {
    MyRMLlamaConnector connector = new MyRMLlamaConnector();
    final List<RMEvent> rmEvents = new ArrayList<RMEvent>();

    CacheRMConnector cache = new CacheRMConnector(
        new Configuration(false), connector);

    cache.setLlamaAMCallback(new RMListener() {
      @Override
      public void stoppedByRM() {
      }

      @Override
      public void onEvent(List<RMEvent> events) {
        rmEvents.addAll(events);
      }
    });

    cache.start();
    cache.getNodes();
    cache.register("q");

    PlacedResourceImpl pr1 = TestUtils.createPlacedResourceImpl("l1",
        Resource.Locality.MUST, 1, 1024);

    cache.reserve(Arrays.asList((RMResource) pr1));

    Assert.assertTrue(connector.invoked.contains("reserve"));
    Assert.assertTrue(rmEvents.isEmpty());

    pr1.setAllocationInfo("l1", 1, 1024);
    pr1.setRmResourceId("rm1");

    cache.release(Arrays.asList((RMResource) pr1), false);

    connector.invoked.clear();

    PlacedResourceImpl pr2 = TestUtils.createPlacedResourceImpl("l1",
        Resource.Locality.MUST, 1, 1024);

    cache.reserve(Arrays.asList((RMResource) pr2));

    Assert.assertFalse(connector.invoked.contains("reserve"));
    Assert.assertFalse(rmEvents.isEmpty());
    Assert.assertEquals(pr2.getResourceId(), rmEvents.get(0).getResourceId());
    cache.unregister();
    cache.stop();
  }

  @Test
  public void testDoNotCache() throws Exception {
    MyRMLlamaConnector connector = new MyRMLlamaConnector();

    CacheRMConnector cache = new CacheRMConnector(
        new Configuration(false), connector);

    cache.setLlamaAMCallback(new RMListener() {
      @Override
      public void stoppedByRM() {
      }

      @Override
      public void onEvent(List<RMEvent> events) {
      }
    });

    cache.start();
    cache.getNodes();
    cache.register("q");

    PlacedResourceImpl pr1 = TestUtils.createPlacedResourceImpl("l1",
        Resource.Locality.MUST, 1, 1024);


    cache.reserve(Arrays.asList((RMResource) pr1));
    pr1.setAllocationInfo("'l1", 1, 1024);
    pr1.setRmResourceId("rm1");

    cache.release(Arrays.asList((RMResource) pr1), false);

    Assert.assertFalse(connector.invoked.contains("release"));

    cache.release(Arrays.asList((RMResource) pr1), true);

    Assert.assertTrue(connector.invoked.contains("release"));

    cache.unregister();
    cache.stop();
  }

  @Test
  public void testEviction() throws Exception {
    MyRMLlamaConnector connector = new MyRMLlamaConnector();

    CacheRMConnector cache = new CacheRMConnector(
        new Configuration(false), connector);

    cache.setLlamaAMCallback(new RMListener() {
      @Override
      public void stoppedByRM() {
      }

      @Override
      public void onEvent(List<RMEvent> events) {
      }
    });

    cache.start();
    cache.getNodes();
    cache.register("q");

    PlacedResourceImpl pr1 = TestUtils.createPlacedResourceImpl("l1",
        Resource.Locality.MUST, 1, 1024);


    cache.reserve(Arrays.asList((RMResource) pr1));
    pr1.setAllocationInfo("'l1", 1, 1024);
    pr1.setRmResourceId("rm1");

    cache.release(Arrays.asList((RMResource) pr1), false);
    Assert.assertFalse(connector.invoked.contains("release"));

    manualClock.increment(ResourceCache.EVICTION_IDLE_TIMEOUT_DEFAULT+1);
    Thread.sleep(100);

    Assert.assertTrue(connector.invoked.contains("release"));

    cache.unregister();
    cache.stop();
  }

  @Test
  public void testEmptyCache() throws Exception {
    MyRMLlamaConnector connector = new MyRMLlamaConnector();

    CacheRMConnector cache = new CacheRMConnector(
        new Configuration(false), connector);

    cache.setLlamaAMCallback(new RMListener() {
      @Override
      public void stoppedByRM() {
      }

      @Override
      public void onEvent(List<RMEvent> events) {
      }
    });

    cache.start();
    cache.getNodes();
    cache.register("q");

    PlacedResourceImpl pr1 = TestUtils.createPlacedResourceImpl("l1",
        Resource.Locality.MUST, 1, 1024);


    cache.reserve(Arrays.asList((RMResource) pr1));
    pr1.setAllocationInfo("'l1", 1, 1024);
    pr1.setRmResourceId("rm1");

    cache.release(Arrays.asList((RMResource) pr1), false);
    Assert.assertFalse(connector.invoked.contains("release"));

    cache.emptyCache();

    Assert.assertTrue(connector.invoked.contains("release"));

    cache.unregister();
    cache.stop();
  }

}
