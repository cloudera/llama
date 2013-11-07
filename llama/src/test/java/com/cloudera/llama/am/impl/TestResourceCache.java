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
package com.cloudera.llama.am.impl;

import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.util.Clock;
import com.cloudera.llama.util.ManualClock;
import com.cloudera.llama.util.UUID;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestResourceCache {
  private ManualClock manualClock = new ManualClock();

  @Before
  public void setup() {
    Clock.setClock(manualClock);
  }

  @After
  public void destroy() {
    Clock.setClock(Clock.SYSTEM);
  }

  private void testTimeoutEvictionPolicy(long timeout) throws Exception {
    ResourceCache.TimeoutEvictionPolicy ep =
        new ResourceCache.TimeoutEvictionPolicy();

    Configuration conf = new Configuration(false);
    if (timeout > 0) {
      conf.setLong(ResourceCache.EVICTION_IDLE_TIMEOUT_KEY, timeout);
    }
    ep.setConf(conf);

    long expected = (timeout == 0)
                    ? ResourceCache.EVICTION_IDLE_TIMEOUT_DEFAULT : timeout;
    Assert.assertEquals(expected, ep.getTimeout());

    manualClock.set(1000);
    ResourceCache.CachedResource cr =
        Mockito.mock(ResourceCache.CachedResource.class);
    Mockito.when(cr.getCachedOn()).thenReturn(1000l);
    Assert.assertFalse(ep.shouldEvict(cr));
    manualClock.increment(ep.getTimeout() - 1);
    Assert.assertFalse(ep.shouldEvict(cr));
    manualClock.increment(1);
    Assert.assertTrue(ep.shouldEvict(cr));
    manualClock.increment(1);
    Assert.assertTrue(ep.shouldEvict(cr));
  }

  @Test
  public void testDefaultTimeoutEvictionPolicy() throws Exception {
    testTimeoutEvictionPolicy(0);
  }

  @Test
  public void testCustomTimeoutEvictionPolicy() throws Exception {
    testTimeoutEvictionPolicy(10);
  }

  @Test
  public void testKey() {
    ResourceCache.Entry entry = Mockito.mock(ResourceCache.Entry.class);
    Mockito.when(entry.getMemoryMb()).thenReturn(1024);
    Mockito.when(entry.getCpuVCores()).thenReturn(1);
    ResourceCache.Key k1 = new ResourceCache.Key(entry);
    ResourceCache.Key ka = new ResourceCache.Key(entry);
    Mockito.when(entry.getMemoryMb()).thenReturn(1024);
    Mockito.when(entry.getCpuVCores()).thenReturn(2);
    ResourceCache.Key k2 = new ResourceCache.Key(entry);
    Mockito.when(entry.getMemoryMb()).thenReturn(2048);
    Mockito.when(entry.getCpuVCores()).thenReturn(1);
    ResourceCache.Key k3 = new ResourceCache.Key(entry);
    Assert.assertTrue(k1.compareTo(k1) == 0);
    Assert.assertTrue(k1.compareTo(k2) < 0);
    Assert.assertTrue(k1.compareTo(k3) < 0);
    Assert.assertTrue(k2.compareTo(k1) > 0);
    Assert.assertTrue(k3.compareTo(k1) > 0);
    Assert.assertTrue(k2.compareTo(k3) < 0);
    Assert.assertTrue(k3.compareTo(k2) > 0);
    Assert.assertTrue(k1.equals(k1));
    Assert.assertTrue(k1.equals(ka));
    Assert.assertFalse(k1.equals(k3));
    Assert.assertFalse(k1.equals(k2));
    Assert.assertFalse(k1.equals(null));
    Assert.assertFalse(k1.equals(new Object()));
    Assert.assertTrue(k1.hashCode() == k1.hashCode());
    Assert.assertTrue(k1.hashCode() == ka.hashCode());
    Assert.assertFalse(k1.hashCode() == k3.hashCode());
    Assert.assertFalse(k1.hashCode() == k2.hashCode());
  }

  @Test
  public void testEntry() throws Exception {
    UUID cacheId = UUID.randomUUID();
    Resource resource = new Resource(UUID.randomUUID(), "l1",
        Resource.LocationEnforcement.MUST, 1, 1024);
    PlacedResourceImpl placedResource = new PlacedResourceImpl(resource);
    placedResource.setAllocationInfo(2, 2048, "l11", "rm11");
    ResourceCache.Entry entry1 = new ResourceCache.Entry(cacheId,
        placedResource, 1000l);
    Assert.assertNotNull(entry1.toString());
    Assert.assertFalse(entry1.isValid());
    entry1.setValid(true);
    Assert.assertTrue(entry1.isValid());
    Assert.assertEquals(cacheId, entry1.getCacheId());
    Assert.assertEquals(1000l, entry1.getCachedOn());
    Assert.assertEquals("l11", entry1.getLocation());
    Assert.assertEquals("rm11", entry1.getRmResourceId());
    Assert.assertEquals(2, entry1.getCpuVCores());
    Assert.assertEquals(2048, entry1.getMemoryMb());

    placedResource.setAllocationInfo(2, 2048, "l22", "rm22");
    ResourceCache.Entry entry2 = new ResourceCache.Entry(cacheId,
        placedResource, 1000l);
    Assert.assertTrue(entry1.compareTo(entry1) == 0);
    Assert.assertTrue(entry1.compareTo(entry2) < 0);
    Assert.assertTrue(entry2.compareTo(entry1) > 0);
  }


  private static class CacheListener implements ResourceCache.Listener {
    String resourceEvicted;

    @Override
    public void onEviction(ResourceCache.CachedResource cachedResource) {
      resourceEvicted = cachedResource.getRmResourceId();
    }
  }

  @Test
  public void testCacheStartStop() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false), listener);
    try {
      cache.start();
    } finally {
      cache.stop();
    }
  }

  @Test
  public void testCacheEviction() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false),
        listener);
    try {
      cache.start();
      manualClock.increment(ResourceCache.EVICTION_IDLE_TIMEOUT_DEFAULT + 1);
      Thread.sleep(100); //to ensure eviction thread runs
      Resource r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 1024);
      PlacedResourceImpl pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(1, 1024, "l1", "rm1");
      cache.cache(pr1);
      Assert.assertNull(listener.resourceEvicted);
      manualClock.increment(ResourceCache.EVICTION_IDLE_TIMEOUT_DEFAULT + 1);
      Thread.sleep(100); //to ensure eviction thread runs
      Assert.assertEquals("rm1", listener.resourceEvicted);
    } finally {
      cache.stop();
    }
  }

  @Test
  public void testCacheSize() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false),
        listener);
    try {
      cache.start();

      Assert.assertEquals(0, cache.getSize());
      Assert.assertEquals(0, cache.getComputedSize());

      Resource r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 1024);
      PlacedResourceImpl pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(1, 1024, "l1", "rm1");
      cache.cache(pr1);

      Assert.assertNull(listener.resourceEvicted);
      Assert.assertEquals(1, cache.getSize());
      Assert.assertEquals(1, cache.getComputedSize());

      manualClock.increment(ResourceCache.EVICTION_IDLE_TIMEOUT_DEFAULT / 2 + 1);

      Assert.assertNull(listener.resourceEvicted);
      Assert.assertEquals(1, cache.getSize());
      Assert.assertEquals(1, cache.getComputedSize());

      pr1.setAllocationInfo(1, 1024, "l1", "rm2");
      cache.cache(pr1);

      Assert.assertNull(listener.resourceEvicted);
      Assert.assertEquals(2, cache.getSize());
      Assert.assertEquals(2, cache.getComputedSize());

      manualClock.increment(ResourceCache.EVICTION_IDLE_TIMEOUT_DEFAULT / 2 + 1);
      Thread.sleep(100); //to ensure eviction thread runs

      Assert.assertEquals("rm1", listener.resourceEvicted);
      Assert.assertEquals(1, cache.getSize());
      Assert.assertEquals(1, cache.getComputedSize());

      manualClock.increment(ResourceCache.EVICTION_IDLE_TIMEOUT_DEFAULT / 2 + 1);
      Thread.sleep(100); //to ensure eviction thread runs

      Assert.assertEquals("rm2", listener.resourceEvicted);
      Assert.assertEquals(0, cache.getSize());
      Assert.assertEquals(0, cache.getComputedSize());

    } finally {
      cache.stop();
    }
  }

  @Test
  public void testCacheRemoveById() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false),
        listener);
    try {
      cache.start();

      Resource r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 1024);
      PlacedResourceImpl pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(1, 1024, "l1", "rm1");
      UUID id1 = cache.cache(pr1);

      pr1.setAllocationInfo(1, 1024, "l1", "rm2");
      UUID id2 = cache.cache(pr1);

      Assert.assertEquals(2, cache.getSize());
      Assert.assertEquals(2, cache.getComputedSize());

      ResourceCache.CachedResource cr1 = cache.findAndRemove(id1);
      Assert.assertNotNull(cr1);
      Assert.assertEquals("rm1", cr1.getRmResourceId());

      Assert.assertEquals(1, cache.getSize());
      Assert.assertEquals(1, cache.getComputedSize());

      Assert.assertNull(cache.findAndRemove(id1));

      ResourceCache.CachedResource cr2 = cache.findAndRemove(id2);
      Assert.assertNotNull(cr2);
      Assert.assertEquals("rm2", cr2.getRmResourceId());

      Assert.assertEquals(0, cache.getSize());
      Assert.assertEquals(0, cache.getComputedSize());

      Assert.assertNull(cache.findAndRemove(id2));

      Assert.assertNull(listener.resourceEvicted);

    } finally {
      cache.stop();
    }
  }

  @Test
  public void testCacheBiggerFindMustLocation() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false),
        listener);
    try {
      cache.start();

      Resource r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 512);
      PlacedResourceImpl pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(1, 512, "l1", "rm1");
      cache.cache(pr1);

      r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 2, 1024);
      pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(2, 1024, "l1", "rm2");
      cache.cache(pr1);

      Resource r2 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 1024);
      PlacedResourceImpl pr2 = new PlacedResourceImpl(r2);

      ResourceCache.CachedResource cr = cache.findAndRemove(pr2);
      Assert.assertNotNull(cr);

      Assert.assertEquals("rm2", cr.getRmResourceId());

    } finally {
      cache.stop();
    }
  }

  @Test
  public void testCacheBiggerFindAnyPreferredLocation() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false),
        listener);
    try {
      cache.start();

      Resource r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 512);
      PlacedResourceImpl pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(1, 512, "l1", "rm1");
      cache.cache(pr1);

      r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 2, 1024);
      pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(2, 1024, "l1", "rm2");
      cache.cache(pr1);

      Resource r2 = new Resource(UUID.randomUUID(), "l2",
          Resource.LocationEnforcement.PREFERRED, 1, 1024);
      PlacedResourceImpl pr2 = new PlacedResourceImpl(r2);

      ResourceCache.CachedResource cr1 = cache.findAndRemove(pr2);
      Assert.assertNotNull(cr1);
      Assert.assertEquals("rm2", cr1.getRmResourceId());

      cache.cache(pr1);

      Resource r3 = new Resource(UUID.randomUUID(), "l2",
          Resource.LocationEnforcement.DONT_CARE, 1, 1024);
      PlacedResourceImpl pr3 = new PlacedResourceImpl(r3);

      ResourceCache.CachedResource cr2 = cache.findAndRemove(pr3);
      Assert.assertNotNull(cr2);

      Assert.assertEquals("rm2", cr2.getRmResourceId());

    } finally {
      cache.stop();
    }
  }

  @Test
  public void testCacheExactFindPreferredAndAnyLocation() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false),
        listener);
    try {
      cache.start();

      Resource r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 1024);
      PlacedResourceImpl pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(1, 1024, "l1", "rm1");
      cache.cache(pr1);
      cache.cache(pr1);

      Resource r2 = new Resource(UUID.randomUUID(), "l2",
          Resource.LocationEnforcement.PREFERRED, 1, 1024);
      PlacedResourceImpl pr2 = new PlacedResourceImpl(r2);
      ResourceCache.CachedResource cr = cache.findAndRemove(pr2);
      Assert.assertNotNull(cr);

      Resource r3 = new Resource(UUID.randomUUID(), "l2",
          Resource.LocationEnforcement.DONT_CARE, 1, 1024);
      PlacedResourceImpl pr3 = new PlacedResourceImpl(r3);
      cr = cache.findAndRemove(pr3);
      Assert.assertNotNull(cr);

      Assert.assertEquals("l1", cr.getLocation());
      Assert.assertTrue(cr.getCpuVCores() >= pr2.getCpuVCores());
      Assert.assertTrue(cr.getMemoryMb() >= pr2.getMemoryMb());
      Assert.assertEquals(0, cache.getSize());
      Assert.assertEquals(0, cache.getComputedSize());
    } finally {
      cache.stop();
    }
  }

  @Test
  public void testCacheMissMustLocation() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false),
        listener);
    try {
      cache.start();

      Resource r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 1024);
      PlacedResourceImpl pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(1, 1024, "l1", "rm1");
      cache.cache(pr1);

      Resource r2 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 2, 1024);
      PlacedResourceImpl pr2 = new PlacedResourceImpl(r2);

      Assert.assertNull(cache.findAndRemove(pr2));

      Resource r3 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 2048);
      PlacedResourceImpl pr3 = new PlacedResourceImpl(r3);

      Assert.assertNull(cache.findAndRemove(pr3));

      Resource r4 = new Resource(UUID.randomUUID(), "l2",
          Resource.LocationEnforcement.MUST, 1, 1024);
      PlacedResourceImpl pr4 = new PlacedResourceImpl(r4);

      Assert.assertNull(cache.findAndRemove(pr4));

      Assert.assertEquals(1, cache.getSize());
      Assert.assertEquals(1, cache.getComputedSize());

    } finally {
      cache.stop();
    }
  }

  @Test
  public void testCacheMissPreferredAndAnyLocation() throws Exception {
    CacheListener listener = new CacheListener();
    ResourceCache cache = new ResourceCache("q", new Configuration(false),
        listener);
    try {
      cache.start();

      Resource r1 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 1024);
      PlacedResourceImpl pr1 = new PlacedResourceImpl(r1);
      pr1.setAllocationInfo(1, 1024, "l1", "rm1");
      cache.cache(pr1);

      Resource r2 = new Resource(UUID.randomUUID(), "l2",
          Resource.LocationEnforcement.PREFERRED, 2, 1024);
      PlacedResourceImpl pr2 = new PlacedResourceImpl(r2);
      Assert.assertNull(cache.findAndRemove(pr2));

      Resource r3 = new Resource(UUID.randomUUID(), "l1",
          Resource.LocationEnforcement.MUST, 1, 2048);
      PlacedResourceImpl pr3 = new PlacedResourceImpl(r3);

      Assert.assertNull(cache.findAndRemove(pr3));

      Assert.assertEquals(1, cache.getSize());
      Assert.assertEquals(1, cache.getComputedSize());

    } finally {
      cache.stop();
    }
  }
}
