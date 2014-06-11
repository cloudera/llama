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
import com.cloudera.llama.am.api.NodeInfo;
import com.cloudera.llama.am.spi.RMConnector;
import com.cloudera.llama.am.spi.RMListener;
import com.cloudera.llama.am.spi.RMResource;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.MetricRegistry;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * <code>PhasingOutRMConnector</code> implementation that handles the token expiry
 * issue in RM.
 */
/**
 * <code>PhasingOutRMConnector</code> implementation that handles the token
 * expiry issue in RM. It recycles the underneath RM connector after a fixed
 * interval.
 * <p/>
 * The following property drive the logic of this class:
 * <ul>
 * <li>{@link LlamaAM#RM_CONNECTOR_RECYCLE_INTERVAL_KEY}</li>
 * </ul>
 */

public class PhasingOutRMConnector implements RMConnector, Runnable {
  private static final Logger LOG =
      LoggerFactory.getLogger(PhasingOutRMConnector.class);
  private final ScheduledFuture<?> future;
  private final RmConnectorCreator newConnectorCreator;

  private Configuration conf;
  private MetricRegistry metricRegistry;
  private RMListener listener;

  private RMConnector active;
  private RMConnector previous;

  synchronized RMConnector[] getConnectors() {
    if (previous != null) {
      return new RMConnector[] {active, previous};
    } else {
      return new RMConnector[] {active};
    }
  }

  public interface RmConnectorCreator {
    RMConnector create();
  };

  public PhasingOutRMConnector(Configuration conf,
                               ScheduledExecutorService stp,
                               RmConnectorCreator newConnectorCreator) throws LlamaException {
    this.conf = conf;
    this.newConnectorCreator = newConnectorCreator;
    this.active = newConnectorCreator.create();

    long interval = conf.getLong(LlamaAM.RM_CONNECTOR_RECYCLE_INTERVAL_KEY,
        LlamaAM.RM_CONNECTOR_RECYCLE_INTERVAL_DEFAULT);
    this.future = stp.scheduleAtFixedRate(this, interval, interval,
        TimeUnit.MINUTES);
  }

  @Override
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
    for(RMConnector connector : getConnectors()) {
      connector.setMetricRegistry(metricRegistry);
    }
  }

  @Override
  public boolean hasResources() {
    for(RMConnector connector : getConnectors()) {
      if (connector.hasResources()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void deleteAllReservations() throws LlamaException {
    active.deleteAllReservations();
  }

  @Override
  public void setRMListener(RMListener listener) {
    this.listener = listener;
    for(RMConnector connector : getConnectors()) {
      connector.setRMListener(listener);
    }
  }

  @Override
  public void start() throws LlamaException {
    active.start();

  }

  @Override
  public void stop() {
    for(RMConnector connector : getConnectors()) {
      connector.stop();
    }
    future.cancel(true);
  }

  @Override
  public void register(String queue) throws LlamaException {
    active.register(queue);
  }

  @Override
  public void unregister() {
    for(RMConnector connector : getConnectors()) {
      connector.unregister();
    }
  }

  @Override
  public List<NodeInfo> getNodes() throws LlamaException {
    return active.getNodes();
  }

  @Override
  public void reserve(Collection<RMResource> resources) throws LlamaException {
    active.reserve(resources);
  }

  @Override
  public void release(Collection<RMResource> resources, boolean doNotCache)
      throws LlamaException {
    for(RMConnector connector : getConnectors()) {
      connector.release(resources, doNotCache);
      doNotCache = true;
    }
  }

  @Override
  public boolean reassignResource(Object rmResourceId, UUID resourceId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void emptyCache() throws LlamaException {
    for(RMConnector connector : getConnectors()) {
      connector.emptyCache();
    }
  }

  public RMConnector getConnector() {
    return this.active;
  }

  @Override
  public void run() {
    LOG.trace("Running the phaseout for RM connector");
    // We need to create a new connector and discard the old one.
    RMConnector newConnector = null;
    newConnector = newConnectorCreator.create();
    newConnector.setRMListener(listener);
    newConnector.setMetricRegistry(metricRegistry);

    List<RMConnector> oldConnectors = new ArrayList<RMConnector>();
    synchronized (this) {
      oldConnectors.add(previous);
      if (!active.hasResources()) {
        oldConnectors.add(active);
        active = null;
      }
      previous = active;
      active = newConnector;
    }
    for(RMConnector old : oldConnectors) {
      if (old != null) {
        if (old.hasResources()) {
          LOG.warn("The previous RMConnector for queue still has resources which " +
              "were not released yet.");
        }
        LOG.trace("Stopping the old RM connector {}.", old);
        old.stop();
      }
    }
  }
}
