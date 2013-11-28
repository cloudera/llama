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

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.RMResource;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.util.Clock;
import com.cloudera.llama.util.FastFormat;
import com.cloudera.llama.util.ParamChecker;
import com.cloudera.llama.util.UUID;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ResourceCache {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceCache.class);

  public interface CachedRMResource extends RMResource {

    public long getCachedOn();

  }

  public interface EvictionPolicy {
    public boolean shouldEvict(CachedRMResource resource);
  }

  public interface Listener {
    public void onEviction(CachedRMResource cachedRMResource);
  }

  public static final String PREFIX = LlamaAM.PREFIX_KEY + "resources.caching.";

  public static final String EVICTION_POLICY_CLASS_KEY =
      PREFIX + "eviction.policy.class";

  public static final String EVICTION_RUN_INTERVAL_KEY =
      PREFIX + "eviction.run.interval.timeout.ms";

  public static final int EVICTION_RUN_INTERVAL_DEFAULT = 5000;

  public static final String EVICTION_IDLE_TIMEOUT_KEY =
      PREFIX + "eviction.timeout.policy.idle.timeout.ms";

  public static final int EVICTION_IDLE_TIMEOUT_DEFAULT = 30000;

  public static class TimeoutEvictionPolicy
      implements EvictionPolicy, Configurable {

    private Configuration conf;
    private long timeout;

    @Override
    public void setConf(Configuration conf) {
      this.conf = conf;
      timeout = conf.getInt(EVICTION_IDLE_TIMEOUT_KEY,
          EVICTION_IDLE_TIMEOUT_DEFAULT);
    }

    public long getTimeout() {
      return timeout;
    }

    @Override
    public Configuration getConf() {
      return conf;
    }

    @Override
    public boolean shouldEvict(CachedRMResource resource) {
      return ((Clock.currentTimeMillis() - resource.getCachedOn()) - timeout)
          >= 0;
    }
  }

  static class Key implements Comparable<Key> {
    private final int cpuVCores;
    private final int memoryMb;

    public Key(Entry entry) {
      this.cpuVCores = entry.getCpuVCores();
      this.memoryMb = entry.getMemoryMbs();
    }

    @Override
    public int compareTo(Key o) {
      int comp = memoryMb - o.memoryMb;
      if (comp == 0) {
        comp = cpuVCores - o.cpuVCores;
      }
      return comp;
    }

    @Override
    public int hashCode() {
      return memoryMb + cpuVCores << 16;
    }

    @Override
    public boolean equals(Object obj) {
      return (obj != null && obj instanceof Key) && compareTo((Key) obj) == 0;
    }
  }

  static class Entry implements Comparable<Entry>, CachedRMResource {
    private final UUID cacheId;
    private final long cachedOn;
    private final Object rmResourceId;
    private final String location;
    private final int cpuVCores;
    private final int memoryMb;
    private final Map<String, Object> rmData;
    private volatile boolean valid;

    Entry(String location, int cpuVCores, int memoryMb) {
      cacheId = null;
      cachedOn = 0;
      rmResourceId = null;
      this.location = location;
      this.cpuVCores = cpuVCores;
      this.memoryMb = memoryMb;
      rmData = null;
    }

    Entry(UUID cacheId, RMResource resource, long cachedOn) {
      this.cacheId = cacheId;
      this.cachedOn = cachedOn;
      rmResourceId = resource.getRmResourceId();
      location = resource.getLocation();
      cpuVCores = resource.getCpuVCores();
      memoryMb = resource.getMemoryMbs();
      rmData = resource.getRmData();
    }

    void setValid(boolean valid) {
      this.valid = valid;
    }

    @Override
    public int compareTo(Entry o) {
      return getLocation().compareTo(o.getLocation());
    }

    @Override
    public UUID getResourceId() {
      return cacheId;
    }

    @Override
    public long getCachedOn() {
      return cachedOn;
    }

    @Override
    public Object getRmResourceId() {
      return rmResourceId;
    }

    @Override
    public Locality getLocalityAsk() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getLocationAsk() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCpuVCoresAsk() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getMemoryMbsAsk() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getLocation() {
      return location;
    }

    @Override
    public int getCpuVCores() {
      return cpuVCores;
    }

    @Override
    public int getMemoryMbs() {
      return memoryMb;
    }

    @Override
    public Map<String, Object> getRmData() {
      return rmData;
    }

    @Override
    public void setRmResourceId(Object rmResourceId) {
      throw new UnsupportedOperationException();
    }

    public boolean isValid() {
      return valid;
    }

    private static final String TO_STRING = "ResourceCache [cacheId: {} " +
        "cachedOn: {} rmResourceId: {} location: {} cpuVCores: {} memoryMb: {}]";

    @Override
    public String toString() {
      return FastFormat.format(TO_STRING, getResourceId(), getCachedOn(),
          getRmResourceId(), getLocation(), getCpuVCores(), getMemoryMbs());
    }

  }

  private final String queue;
  private final NavigableMap<Key, List<Entry>> cache;
  private final Map<UUID, Entry> idToCacheEntryMap;
  private final EvictionPolicy evictionPolicy;
  private volatile boolean running;
  private final int evictionRunInterval;
  private Thread evictionThread;
  private final Listener listener;

  public ResourceCache(String queue, Configuration conf, Listener listener) {
    this.queue = ParamChecker.notEmpty(queue, "queue");
    this.listener = ParamChecker.notNull(listener, "listener");
    cache = new TreeMap<Key, List<Entry>>();
    idToCacheEntryMap = new HashMap<UUID, Entry>();
    Class<? extends EvictionPolicy> klass =
        conf.getClass(EVICTION_POLICY_CLASS_KEY, TimeoutEvictionPolicy.class,
            EvictionPolicy.class);
    evictionPolicy = ReflectionUtils.newInstance(klass, conf);
    evictionRunInterval = conf.getInt(EVICTION_RUN_INTERVAL_KEY,
        EVICTION_RUN_INTERVAL_DEFAULT);
  }


  public synchronized void start() {
    if (running) {
      throw new IllegalStateException("Already started");
    }
    LOG.debug("EvictionPolicy '{}'", evictionPolicy.getClass().getSimpleName());
    LOG.debug("Eviction run interval '{}'ms", evictionRunInterval);
    running = true;
    evictionThread = new Thread("llama-resource-cache-eviction:" + queue) {
      @Override
      public void run() {
        while (running) {
          try {
            Clock.sleep(evictionRunInterval);
          } catch (InterruptedException ex) {
            //NOP
          }
          if (running) {
            runEviction();
          }
        }
      }
    };
    evictionThread.setDaemon(true);
    evictionThread.start();
  }

  void runEviction() {
    List<Entry> entries;
    synchronized (ResourceCache.this) {
      entries = new ArrayList<Entry>(idToCacheEntryMap.values());
    }
    LOG.debug("Eviction run, processing '{}' entries", entries.size());
    int counter = 0;
    for (Entry entry : entries) {
      if (entry.isValid() && evictionPolicy.shouldEvict(entry)) {
        if (findAndRemove(entry.getResourceId()) != null) {
          try {
            listener.onEviction(entry);
          } catch (Throwable ex) {
            LOG.error("Listener error processing eviction for '{}', {}",
                entry.getRmResourceId(), ex.toString(), ex);
          }
          LOG.debug("Evicted '{}' from queue '{}'",
              entry.getRmResourceId(), queue);
          counter++;
        }
      }
    }
    LOG.debug("Eviction run, evicted '{}' entries", counter);
  }

  public synchronized void stop() {
    running = false;
    evictionThread.interrupt();
    try {
      evictionThread.join();
    } catch (InterruptedException ex) {
      //NOP
    }
  }

  public synchronized UUID cache(RMResource resource) {
    UUID id = UUID.randomUUID();
    Entry entry = new Entry(id, resource, Clock.currentTimeMillis());
    entry.setValid(true);
    idToCacheEntryMap.put(id, entry);
    Key key = new Key(entry);
    List<Entry> list = cache.get(key);
    if (list == null) {
      list = new ArrayList<Entry>();
      cache.put(key, list);
    }
    int idx = Collections.binarySearch(list, entry);
    if (idx >= 0) {
      list.add(idx, entry);
    } else {
      list.add(- (idx + 1), entry);
    }
    return id;
  }

  private enum Mode {SAME_REF, STRICT_LOCATION, ANY_LOCATION}

  private CachedRMResource findAndRemove(Entry entry, Mode mode) {
    ParamChecker.notNull(entry, "entry");
    ParamChecker.notNull(mode, "mode");
    Entry found = null;
    Key key = new Key(entry);
    Map.Entry<Key, List<Entry>> cacheEntry = cache.ceilingEntry(key);
    List<Entry> list = (cacheEntry != null) ? cacheEntry.getValue() : null;
    if (list != null) {
      int idx = Collections.binarySearch(list, entry);
      if (idx >= 0) {
        switch (mode) {
          case SAME_REF:
            for (int i = idx; i < list.size() && entry.compareTo(list.get(i)) == 0; i++) {
              if (entry == list.get(i)) {
                found = entry;
                list.remove(i);
                break;
              }
            }
            break;
          case STRICT_LOCATION:
          case ANY_LOCATION:
            found = list.remove(idx);
            break;
        }
      } else {
        switch (mode) {
          case SAME_REF:
            throw new RuntimeException("Inconsistent state");
          case STRICT_LOCATION:
            break;
          case ANY_LOCATION:
            if ( -(idx + 1) <= list.size()) {
              found = list.remove(- (idx + 1) - 1);
            }
            break;
        }
      }
      if (found != null) {
        idToCacheEntryMap.remove(found.getResourceId());
        if (list.isEmpty()) {
          cache.remove(key);
        }
        found.setValid(false);
      }
    }
    return found;
  }

  public synchronized CachedRMResource findAndRemove(RMResource resource) {
    Mode mode = (resource.getLocalityAsk() == Resource.Locality.MUST)
                ? Mode.STRICT_LOCATION : Mode.ANY_LOCATION;
    return findAndRemove(new Entry(resource.getLocationAsk(),
        resource.getCpuVCoresAsk(), resource.getMemoryMbsAsk()), mode);
  }

  public synchronized CachedRMResource findAndRemove(UUID cacheId) {
    Entry found = idToCacheEntryMap.remove(cacheId);
    if (found != null && findAndRemove(found, Mode.SAME_REF) == null) {
      LOG.error("Inconsistency in cache for cacheId '{}' rmResourceId '{}'",
          cacheId, found.getRmResourceId());
    }
    return found;
  }

  public synchronized int getSize() {
    return idToCacheEntryMap.size();
  }

}
