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

import com.cloudera.llama.am.api.LlamaAMException;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.spi.RMLlamaAMCallback;
import com.cloudera.llama.am.spi.RMLlamaAMConnector;
import com.cloudera.llama.am.spi.RMPlacedResource;
import com.cloudera.llama.am.spi.RMResourceChange;
import com.cloudera.llama.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class RMLlamaAMConnectorCache implements RMLlamaAMConnector,
    RMLlamaAMCallback, ResourceCache.Listener {
  private static final Logger LOG =
      LoggerFactory.getLogger(RMLlamaAMConnectorCache.class);

  private Configuration conf;
  private ResourceCache cache;
  private final RMLlamaAMConnector connector;
  private RMLlamaAMCallback callback;

  public RMLlamaAMConnectorCache(Configuration conf,
      RMLlamaAMConnector connector) {
    this.conf = conf;
    this.connector = connector;
    connector.setLlamaAMCallback(this);
  }

  @Override
  public void setLlamaAMCallback(RMLlamaAMCallback callback) {
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
    cache = new ResourceCache(queue, conf, this);
    cache.start();
    connector.register(queue);
  }

  @Override
  public void unregister() {
    connector.unregister();
    cache.stop();
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    return connector.getNodes();
  }

  @Override
  public void reserve(Collection<RMPlacedResource> resources)
      throws LlamaAMException {
    List<RMPlacedResource> list = new ArrayList<RMPlacedResource>(resources);
    List<RMResourceChange> changes = new ArrayList<RMResourceChange>();
    Iterator<RMPlacedResource> it = list.iterator();
    while (it.hasNext()) {
      RMPlacedResource resource = it.next();
      ResourceCache.CachedResource cached = cache.findAndRemove(resource);
      if (cached != null) {
        LOG.debug("Using cached resource '{}' for placed resource '{}'",
            cached, resource);
        it.remove();
        connector.reassignResource(cached.getRmResourceId(),
            resource.getClientResourceId());
        RMResourceChange change = RMResourceChange.createResourceAllocation(
            resource.getClientResourceId(), cached.getRmResourceId(),
            cached.getCpuVCores(), cached.getMemoryMb(), cached.getLocation());
        changes.add(change);
      }
    }
    connector.reserve(resources);
    changesFromRM(changes);
  }

  @Override
  public void release(Collection<RMPlacedResource> resources)
      throws LlamaAMException {
    List<RMPlacedResource> list = new ArrayList<RMPlacedResource>(resources);
    Iterator<RMPlacedResource> it = list.iterator();
    while (it.hasNext()) {
      RMPlacedResource resource = it.next();
      if (resource.getStatus() == PlacedResource.Status.ALLOCATED) {
        UUID cacheId = cache.cache(resource);
        if (connector.reassignResource(resource.getRmResourceId(), cacheId)) {
          LOG.debug("Caching released resource '{}'", resource);
          it.remove();
        } else {
          cache.findAndRemove(cacheId);
          LOG.warn(
              "RMConnector did not reassign '{}', releasing and discarding it",
              resource.getRmResourceId());
          connector.release(resources);
        }
      } else {
        connector.release(resources);
      }
    }
  }

  @Override
  public boolean reassignResource(String rmResourceId, UUID resourceId) {
    return false;
  }

  @Override
  public void stoppedByRM() {
    callback.stoppedByRM();
  }

  @Override
  public void changesFromRM(List<RMResourceChange> changes) {
    Iterator<RMResourceChange> it = changes.iterator();
    while (it.hasNext()) {
      RMResourceChange change = it.next();
      if (cache.findAndRemove(change.getClientResourceId()) != null) {
        LOG.warn("Cached resource '{}' status changed to '{}', discarding it " +
            "from cache", change.getRmResourceId(), change.getStatus());
        it.remove();
      }
    }
    callback.changesFromRM(changes);
  }

  private final Resource DUMMY_RESOURCE = new Resource(UUID.randomUUID(),
      "l", Resource.LocationEnforcement.DONT_CARE, 1, 1);

  @Override
  public void onEviction(ResourceCache.CachedResource cachedResource) {
    RMPlacedResource pr = new PlacedResourceImpl(DUMMY_RESOURCE);
    pr.setRmPayload(cachedResource.getRmPayload());
    try {
      connector.release(Arrays.asList(pr));
    } catch (Throwable ex) {
      LOG.error(
          "Failed to release resource '{}' from RMConnector on eviction, {}",
          cachedResource.getRmResourceId(), ex.toString(), ex);
    }
  }
}
