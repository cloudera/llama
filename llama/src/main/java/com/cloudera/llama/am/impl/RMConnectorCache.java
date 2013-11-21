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
import com.cloudera.llama.am.api.LlamaAMException;
import com.cloudera.llama.am.api.RMResource;
import com.cloudera.llama.am.spi.RMEvent;
import com.cloudera.llama.am.spi.RMListener;
import com.cloudera.llama.am.spi.RMConnector;
import com.cloudera.llama.util.FastFormat;
import com.cloudera.llama.util.UUID;
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

public class RMConnectorCache implements RMConnector,
    RMListener, ResourceCache.Listener {
  private static final Logger LOG =
      LoggerFactory.getLogger(RMConnectorCache.class);

  private static final String METRIC_PREFIX_TEMPLATE = LlamaAM.METRIC_PREFIX +
      "queue({}).cache.";

  private static final String ONE_MIN_CACHE_RATIO_TEMPLATE
      = METRIC_PREFIX_TEMPLATE + "one.min.ratio.gauge";

  private static final String FIVE_MIN_CACHE_RATIO_TEMPLATE
      = METRIC_PREFIX_TEMPLATE + "five.min.ratio.gauge";

  private Configuration conf;
  private ResourceCache cache;
  private final RMConnector connector;
  private RMListener callback;
  private MetricRegistry metricRegistry;
  private String queue;
  private final Meter resourcesAsked;
  private final Meter cacheHits;

  public RMConnectorCache(Configuration conf,
      RMConnector connector) {
    this.conf = conf;
    this.connector = connector;
    connector.setLlamaAMCallback(this);
    resourcesAsked = new Meter();
    cacheHits = new Meter();
  }

  public void setMetricRegistry(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
  }

  @Override
  public void setLlamaAMCallback(RMListener callback) {
    this.callback = callback;
  }

  @Override
  public void start() throws LlamaAMException {
    connector.start();
  }

  @Override
  public void stop() {
    connector.stop();
  }

  @Override
  public void register(String queue) throws LlamaAMException {
    this.queue = queue;
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
      metricRegistry.register(FastFormat.format(ONE_MIN_CACHE_RATIO_TEMPLATE,
          queue), oneMinGauge);
      metricRegistry.register(FastFormat.format(FIVE_MIN_CACHE_RATIO_TEMPLATE,
          queue), fiveMinGauge);
    }
    connector.register(queue);
  }

  @Override
  public void unregister() {
    connector.unregister();
    cache.stop();
    if (metricRegistry != null) {
      metricRegistry.remove(FastFormat.format(ONE_MIN_CACHE_RATIO_TEMPLATE,
          queue));
      metricRegistry.remove(FastFormat.format(FIVE_MIN_CACHE_RATIO_TEMPLATE,
          queue));
    }
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    return connector.getNodes();
  }

  @Override
  public void reserve(Collection<RMResource> resources)
      throws LlamaAMException {
    List<RMResource> list = new ArrayList<RMResource>(resources);
    List<RMEvent> changes = new ArrayList<RMEvent>();
    Iterator<RMResource> it = list.iterator();
    while (it.hasNext()) {
      RMResource resource = it.next();
      ResourceCache.CachedRMResource cached = cache.findAndRemove(resource);
      resourcesAsked.mark();
      if (cached != null) {
        cacheHits.mark();
        LOG.debug("Using cached resource '{}' for placed resource '{}'",
            cached, resource);
        it.remove();
        connector.reassignResource(cached.getRmResourceId(),
            resource.getResourceId());
        RMEvent change = RMEvent.createAllocationEvent(
            resource.getResourceId(), cached.getRmResourceId(),
            cached.getCpuVCores(), cached.getMemoryMbs(), cached.getLocation());
        changes.add(change);
      }
    }
    connector.reserve(resources);
    onEvent(changes);
  }

  @Override
  public void release(Collection<RMResource> resources)
      throws LlamaAMException {
    List<RMResource> list = new ArrayList<RMResource>(resources);
    Iterator<RMResource> it = list.iterator();
    while (it.hasNext()) {
      RMResource resource = it.next();
      if (resource.getRmResourceId() != null) {
        UUID cacheId = cache.cache(resource);
        if (connector.reassignResource(resource.getRmResourceId(), cacheId)) {
          LOG.debug("Caching released resource '{}'", resource);
          it.remove();
        } else {
          cache.findAndRemove(cacheId);
          LOG.warn(
              "RMConnector did not reassign '{}', releasing and discarding it",
              resource.getResourceId());
        }
      }
    }
    if (!list.isEmpty()) {
      connector.release(list);
    }
  }

  @Override
  public boolean reassignResource(Object rmResourceId, UUID resourceId) {
    return false;
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
      }
    }
    callback.onEvent(events);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void onEviction(ResourceCache.CachedRMResource cachedRMResource) {
    RMResource dummyPlacedResource = new PlacedResourceImpl();
    dummyPlacedResource.getRmData().putAll((Map) cachedRMResource.getRmData());
    try {
      connector.release(Arrays.asList(dummyPlacedResource));
    } catch (Throwable ex) {
      LOG.error(
          "Failed to release resource '{}' from RMConnector on eviction, {}",
          cachedRMResource.getRmResourceId(), ex.toString(), ex);
    }
  }
}
