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

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.NodeInfo;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.impl.PlacedResourceImpl;
import com.cloudera.llama.server.MetricUtil;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.am.spi.RMResource;
import com.cloudera.llama.am.spi.RMEvent;
import com.cloudera.llama.am.spi.RMListener;
import com.cloudera.llama.am.spi.RMConnector;
import com.cloudera.llama.util.FastFormat;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * <code>RMConnector</code> implementation that caches resources upon release.
 * <p/>
 * There are two configuration properties that drive the logic of this class:
 * <ul>
 * <li>{@link LlamaAM#EVICTION_POLICY_CLASS_KEY}</li>
 * <li>{@link LlamaAM#EVICTION_RUN_INTERVAL_KEY}</li>
 * <li>{@link LlamaAM#EVICTION_IDLE_TIMEOUT_KEY}</li>
 * </ul>
 */
public class CacheRMConnector implements RMConnector,
    RMListener, ResourceCache.Listener {
  private static final Logger LOG =
      LoggerFactory.getLogger(CacheRMConnector.class);

  private static final String METRIC_PREFIX = LlamaAM.METRIC_PREFIX +
      "queue-cache.";

  private static final String PENDING_RESOURCES_TEMPLATE = METRIC_PREFIX +
      "pending-resources[{}].gauge";

  private static final String CACHED_RESOURCES_TEMPLATE = METRIC_PREFIX +
      "cached-resources[{}].gauge";

  private static final String ONE_MIN_CACHE_RATIO_TEMPLATE = METRIC_PREFIX +
      "one-min-ratio[{}].gauge";

  private static final String FIVE_MIN_CACHE_RATIO_TEMPLATE = METRIC_PREFIX +
      "five-min-ratio[{}].gauge";

  private Configuration conf;
  // pending keeps track of pending resources, so when a resource is released
  // we can check against pending if the released resource can be used to
  // satisfy a pending resource.
  private ResourceStore pending;
  // cache keeps track of cached unused resources, when a resource is requested
  // before propagating the request to the underlying connector, we check if the
  // cache already has a cached resource that could satisfy the requested
  // resource.
  private ResourceCache cache;
  private final RMConnector connector;
  private RMListener callback;
  private MetricRegistry metricRegistry;
  private String queue;
  private final Meter resourcesAsked;
  private final Meter cacheHits;

  public CacheRMConnector(Configuration conf,
      RMConnector connector) {
    this.conf = conf;
    this.connector = connector;
    connector.setRMListener(this);
    resourcesAsked = new Meter();
    cacheHits = new Meter();
  }

  @Override
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
    connector.setMetricRegistry(metricRegistry);
  }

  @Override
  public boolean hasResources() {
    return connector.hasResources();
  }

  @Override
  public void deleteAllReservations() throws LlamaException {
    connector.deleteAllReservations();
  }

  @Override
  public void setRMListener(RMListener listener) {
    this.callback = listener;
  }

  @Override
  public void start() throws LlamaException {
    connector.start();
  }

  @Override
  public void stop() {
    connector.stop();
  }

  @Override
  public void register(String queue) throws LlamaException {
    this.queue = queue;
    pending = new ResourceStore();
    cache = new ResourceCache(queue, conf, this);
    cache.start();
    if (metricRegistry != null) {
      RatioGauge oneMinGauge = new RatioGauge() {
        @Override
        protected Ratio getRatio() {
          return Ratio.of(cacheHits.getOneMinuteRate(),
              resourcesAsked.getOneMinuteRate());
        }
      };
      RatioGauge fiveMinGauge = new RatioGauge() {
        @Override
        protected Ratio getRatio() {
          return Ratio.of(cacheHits.getFiveMinuteRate(),
              resourcesAsked.getFiveMinuteRate());
        }
      };
      MetricUtil.registerGauge(metricRegistry,
          FastFormat.format(PENDING_RESOURCES_TEMPLATE,
          queue), new Gauge<Integer>() {
        @Override
        public Integer getValue() {
          return pending.getSize();
        }
      });
      MetricUtil.registerGauge(metricRegistry,
          FastFormat.format(CACHED_RESOURCES_TEMPLATE,
          queue), new Gauge<Integer>() {
        @Override
        public Integer getValue() {
          return cache.getSize();
        }
      });
      MetricUtil.registerGauge(metricRegistry,
          FastFormat.format(ONE_MIN_CACHE_RATIO_TEMPLATE,
          queue), oneMinGauge);
      MetricUtil.registerGauge(metricRegistry,
          FastFormat.format(FIVE_MIN_CACHE_RATIO_TEMPLATE,
          queue), fiveMinGauge);
    }
    connector.register(queue);
  }

  @Override
  public void unregister() {
    connector.unregister();
    cache.stop();
  }

  @Override
  public List<NodeInfo> getNodes() throws LlamaException {
    return connector.getNodes();
  }

  @Override
  public void reserve(Collection<RMResource> resources)
      throws LlamaException {
    List<RMResource> list = new ArrayList<RMResource>(resources);
    List<RMEvent> changes = new ArrayList<RMEvent>();
    Iterator<RMResource> it = list.iterator();
    while (it.hasNext()) {
      RMResource resource = it.next();
      CacheRMResource cached = cache.findAndRemove(resource);
      resourcesAsked.mark();
      if (cached != null) {
        if (cached.getCpuVCores() != resource.getCpuVCoresAsk() &&
            cached.getMemoryMbs() != resource.getMemoryMbsAsk()) {
          LOG.error("Cache not working properly: Ask cpu: {}, mem: {}. " +
              "Give cpu: {}, mem {}",
              resource.getCpuVCoresAsk(),
              resource.getMemoryMbsAsk(),
              cached.getCpuVCores(), cached.getMemoryMbs());
          }
        cacheHits.mark();
        LOG.debug("Using cached resource '{}' for placed resource '{}'",
            cached, resource);
        it.remove();
        connector.reassignResource(cached.getRmResourceId(),
            resource.getResourceId());
        resource.getRmData().putAll(cached.getRmData());
        RMEvent change = RMEvent.createAllocationEvent(
            resource.getResourceId(), cached.getLocation(),
            cached.getCpuVCores(), cached.getMemoryMbs(),
            cached.getRmResourceId(), cached.getRmData());
        changes.add(change);
      } else {
        LOG.trace("Adding a new pending entry in the cache: {}", resource);
        pending.add(Entry.createStoreEntry(resource));
      }
    }
    if (!list.isEmpty()) {
      connector.reserve(list);
    }
    callback.onEvent(changes);
  }

  @Override
  public void release(Collection<RMResource> resources, boolean doNotCache)
      throws LlamaException {
    List<RMResource> list = new ArrayList<RMResource>(resources);
    List<RMEvent> changes = new ArrayList<RMEvent>();
    Iterator<RMResource> it = list.iterator();
    while (it.hasNext()) {
      RMResource resource = it.next();
      // we cannot synchronize on the resource here since the pending entry is a
      // copy which could be released independently.
      synchronized (this) {
        // Remove any existing entry for the exact current resource.
        pending.findAndRemove(resource.getResourceId());

        if (!doNotCache) {
          if (resource.getRmResourceId() != null) {
            // Find an existing pending entry that can be satisfied with this resource.
            Entry pendingEntry = pending.findAndRemove(resource);

            // Find something that can match the resource requirements and
            // assign this rm resource
            if (pendingEntry == null) {
              LOG.trace("Release: Pending Entry is null. Trying to put " +
                  "resource {} in cache.", resource);
              Entry entry = Entry.createCacheEntry(resource);
              UUID cacheId = entry.getResourceId();
              cache.add(entry);
              if (connector.reassignResource(resource.getRmResourceId(), cacheId)) {
                LOG.debug("Caching released resource '{}'", resource);
                it.remove();
              } else {
                cache.findAndRemove(cacheId);
                LOG.warn("RMConnector did not reassign '{}', releasing and " +
                    "discarding it", resource.getResourceId());
              }
            } else {
              LOG.trace("Release: There is a pending entry {}, trying to " +
                  "reuse resource {}.", pendingEntry, resource);
              connector.release(Arrays.asList((RMResource)pendingEntry), false);
              if (connector.reassignResource(resource.getRmResourceId(),
                  pendingEntry.getResourceId())) {
                LOG.debug(
                    "Reassigning released resource '{}' to pending resource '{}'",
                    resource, pendingEntry);
                it.remove();
                RMEvent change = RMEvent.createAllocationEvent(
                    pendingEntry.getResourceId(), resource.getLocation(),
                    resource.getCpuVCores(), resource.getMemoryMbs(),
                    resource.getRmResourceId(), resource.getRmData());
                changes.add(change);
                pendingEntry.getRmData().putAll(resource.getRmData());
              } else {
                LOG.warn("RMConnector did not reassign '{}', discarding it",
                    resource.getResourceId());
              }
            }
          } else {
            LOG.trace("Resource {} was not allocated yet. Releasing it.", resource);
          }
        } else {
          LOG.trace("Not caching the resource {}. Releasing it.", resource);
        }
      }
    }
  if (!list.isEmpty()) {
      connector.release(list, doNotCache);
    }
    callback.onEvent(changes);
  }

  @Override
  public boolean reassignResource(Object rmResourceId, UUID resourceId) {
    return false;
  }

  @Override
  public void emptyCache() throws LlamaException {
    List<RMResource> cachedList = cache.emptyStore();
    LOG.debug("Emptying cache for queue '{}'", queue);
    connector.release(cachedList, true);
  }

  @Override
  public void stoppedByRM() {
    callback.stoppedByRM();
  }

  @Override
  public void onEvent(List<RMEvent> events) {
    Iterator<RMEvent> it = events.iterator();
    while (it.hasNext()) {
      RMEvent change = it.next();
      if (cache.findAndRemove(change.getResourceId()) != null) {
        LOG.warn("Cached resource '{}' status changed to '{}', discarding it " +
            "from cache", change.getRmResourceId(), change.getStatus());
        it.remove();
      } else if (pending.findAndRemove(change.getResourceId()) == null &&
          change.getStatus() == PlacedResource.Status.ALLOCATED) {
        // we run into a race condition where the the pending resource has been
        // fulfilled with a resource being released and the RM allocated the
        // resources for it. In this case we simply put the just allocated
        // resource in the cache.
        Entry entry = Entry.createCacheEntry(change);
        UUID cacheId = entry.getResourceId();
        cache.add(entry);
        if (connector.reassignResource(entry.getRmResourceId(), cacheId)) {
          LOG.debug("Caching allocated resource '{}'", entry);
          it.remove();
        } else {
          cache.findAndRemove(cacheId);
          LOG.warn("RMConnector did not reassign '{}', releasing and " +
              "discarding it", change.getResourceId());
        }
      }
    }
    callback.onEvent(events);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void onEviction(CacheRMResource cachedRMResource) {
    RMResource dummyPlacedResource = new PlacedResourceImpl();
    dummyPlacedResource.getRmData().putAll((Map) cachedRMResource.getRmData());
    try {
      connector.release(Arrays.asList(dummyPlacedResource), false);
    } catch (Throwable ex) {
      LOG.error(
          "Failed to release resource '{}' from RMConnector on eviction, {}",
          cachedRMResource.getRmResourceId(), ex.toString(), ex);
    }
  }

  //visible for testing
  int getPendingSize() {
    return pending.getSize();
  }

  //visible for testing
  int getCacheSize() {
    return cache.getSize();
  }
}
