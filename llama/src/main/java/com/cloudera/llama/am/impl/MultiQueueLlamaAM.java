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
import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.server.MetricUtil;
import com.cloudera.llama.util.FastFormat;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MultiQueueLlamaAM extends LlamaAMImpl implements LlamaAMListener,
    IntraLlamaAMsCallback {
  private static final Logger LOG = 
      LoggerFactory.getLogger(MultiQueueLlamaAM.class);

  private static final String QUEUES_GAUGE = METRIC_PREFIX + "queues.gauge";
  private static final String RESERVATIONS_GAUGE = METRIC_PREFIX +
      "reservations.gauge";

  private final Map<String, LlamaAM> ams;
  private SingleQueueLlamaAM llamaAMForGetNodes;
  private final ConcurrentHashMap<UUID, String> reservationToQueue;
  private boolean running;

  public MultiQueueLlamaAM(Configuration conf) {
    super(conf);
    ams = new HashMap<String, LlamaAM>();
    reservationToQueue = new ConcurrentHashMap<UUID, String>();
    if (SingleQueueLlamaAM.getRMConnectorClass(conf) == null) {
      throw new IllegalArgumentException(FastFormat.format(
          "RMConnector class not defined in the configuration under '{}'",
          SingleQueueLlamaAM.RM_CONNECTOR_CLASS_KEY));
    }
  }

  @Override
  public synchronized void setMetricRegistry(MetricRegistry metricRegistry) {
    super.setMetricRegistry(metricRegistry);
    for (LlamaAM am : ams.values()) {
      am.setMetricRegistry(metricRegistry);
    }
    if (metricRegistry != null) {
      MetricUtil.registerGauge(metricRegistry, QUEUES_GAUGE,
          new CachedGauge<List<String>>(1, TimeUnit.SECONDS) {
            @Override
            protected List<String> loadValue() {
              synchronized (ams) {
                return new ArrayList<String>(ams.keySet());
              }
            }
          });
      MetricUtil.registerGauge(metricRegistry, RESERVATIONS_GAUGE,
          new Gauge<Integer>() {
            @Override
            public Integer getValue() {
              return reservationToQueue.size();
            }
          });
    }
  }

  // LlamaAMListener API

  @Override
  public void onEvent(LlamaAMEvent event) {
    dispatch(event);
  }

  // LlamaAM API

  private LlamaAM getLlamaAM(String queue, boolean create)
      throws LlamaException {
    LlamaAM am;
    synchronized (ams) {
      am = ams.get(queue);
      if (am == null && create) {
        SingleQueueLlamaAM qAm = new SingleQueueLlamaAM(getConf(), queue);

        boolean throttling = getConf().getBoolean(
            THROTTLING_ENABLED_KEY,
            THROTTLING_ENABLED_DEFAULT);
        throttling = getConf().getBoolean(
            THROTTLING_ENABLED_KEY + "." + queue, throttling);
        LOG.info("Throttling for queue '{}' enabled '{}'", queue,
            throttling);
        if (throttling) {
          ThrottleLlamaAM tAm = new ThrottleLlamaAM(getConf(), queue, qAm);
          tAm.setCallback(this);
          am = tAm;
        } else {
          am = qAm;
        }
        am.setMetricRegistry(getMetricRegistry());
        am.start();
        am.addListener(this);
        ams.put(queue, am);
      }
    }
    return am;
  }

  private Set<LlamaAM> getLlamaAMs() throws LlamaException {
    synchronized (ams) {
      return new HashSet<LlamaAM>(ams.values());
    }
  }


  @Override
  public void start() throws LlamaException {
    for (String queue :
        getConf().getTrimmedStringCollection(INITIAL_QUEUES_KEY)) {
      try {
        getLlamaAM(queue, true);
      } catch (LlamaException ex) {
        stop();
        throw ex;
      }
    }
    llamaAMForGetNodes = new SingleQueueLlamaAM(getConf(), null);
    llamaAMForGetNodes.setCallback(this);
    llamaAMForGetNodes.start();
    running = true;
  }

  @Override
  public void stop() {
    running = false;
    synchronized (ams) {
      for (LlamaAM am : ams.values()) {
        am.stop();
      }
    }
    if (llamaAMForGetNodes != null) {
      llamaAMForGetNodes.stop();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public List<String> getNodes() throws LlamaException {
    return llamaAMForGetNodes.getNodes();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void reserve(UUID reservationId, Reservation reservation)
      throws LlamaException {
    LlamaAM am = getLlamaAM(reservation.getQueue(), true);
    am.reserve(reservationId, reservation);
    reservationToQueue.put(reservationId, reservation.getQueue());
  }

  @SuppressWarnings("deprecation")
  @Override
  public PlacedReservation getReservation(UUID reservationId)
      throws LlamaException {
    PlacedReservation reservation = null;
    String queue = reservationToQueue.get(reservationId);
    if (queue != null) {
      LlamaAM am = getLlamaAM(queue, false);
      if (am != null) {
        reservation = am.getReservation(reservationId);
      } else {
        LOG.warn("Queue '{}' not available anymore", queue);
      }
    } else {
      LOG.warn("getReservation({}), reservationId not found",
          reservationId);
    }
    return reservation;
  }

  @SuppressWarnings("deprecation")
  @Override
  public PlacedReservation releaseReservation(UUID handle, UUID reservationId,
      boolean doNotCache) throws LlamaException {
    PlacedReservation pr = null;
    String queue = reservationToQueue.remove(reservationId);
    if (queue != null) {
      LlamaAM am = getLlamaAM(queue, false);
      if (am != null) {
        pr = am.releaseReservation(handle, reservationId, doNotCache);
      } else {
        LOG.warn("Queue '{}' not available anymore", queue);
      }
    } else {
      LOG.warn("releaseReservation({}), reservationId not found",
          reservationId);
    }
    return pr;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForHandle(UUID handle,
      boolean doNotCache)
      throws LlamaException {
    List<PlacedReservation> reservations = new ArrayList<PlacedReservation>();
    LlamaException thrown = null;
    for (LlamaAM am : getLlamaAMs()) {
      try {
        reservations.addAll(am.releaseReservationsForHandle(handle, doNotCache));
      } catch (LlamaException ex) {
        if (thrown != null) {
          LOG.error("releaseReservationsForHandle({}), error: {}",
              handle, ex.toString(), ex);
        }
        thrown = ex;
      }
    }
    if (thrown != null) {
      throw thrown;
    }
    return reservations;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PlacedReservation> releaseReservationsForQueue(String queue,
      boolean doNotCache)
      throws LlamaException {
    List<PlacedReservation> list;
    LlamaAM am;
    synchronized (ams) {
      am = (doNotCache) ? ams.remove(queue) : ams.get(queue);
    }
    if (am != null) {
      list = am.releaseReservationsForQueue(queue, doNotCache);
      if (doNotCache) {
        am.stop();
      }
    } else {
      list = Collections.EMPTY_LIST;
    }
    return list;
  }

  @Override
  public void emptyCacheForQueue(String queue) throws LlamaException {
    if (queue == ALL_QUEUES) {
      for (LlamaAM am : getLlamaAMs()) {
        am.emptyCacheForQueue(queue);
      }
    } else {
      LlamaAM am = getLlamaAM(queue, false);
      if (am != null) {
        am.emptyCacheForQueue(queue);
      }
    }
  }

  @Override
  public void discardAM(String queue) {
    LOG.warn("discarding queue '{}' and all its reservations", queue);
    synchronized (ams) {
      ams.remove(queue);
      Iterator<Map.Entry<UUID, String>> i =
          reservationToQueue.entrySet().iterator();
      while (i.hasNext()) {
        if (i.next().getValue().equals(queue)) {
          i.remove();
        }
      }
    }
  }

  @Override
  public void discardReservation(UUID reservationId) {
    reservationToQueue.remove(reservationId);
  }

}
