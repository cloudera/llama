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
import com.cloudera.llama.am.api.LlamaAMException;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.server.MetricUtil;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.apache.hadoop.conf.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MultiQueueLlamaAM extends LlamaAMImpl implements LlamaAMListener,
    SingleQueueLlamaAM.Callback  {

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
          "RMLlamaAMConnector class not defined in the configuration under '{}'",
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
  public void handle(LlamaAMEvent event) {
    dispatch(event);
  }

  // LlamaAM API

  private LlamaAM getLlamaAM(String queue) throws LlamaAMException {
    LlamaAM am;
    synchronized (ams) {
      am = ams.get(queue);
      if (am == null) {
        am = new SingleQueueLlamaAM(getConf(), queue, this);
        am.setMetricRegistry(getMetricRegistry());
        am.start();
        am.addListener(this);
        ams.put(queue, am);
      }
    }
    return am;
  }

  private Set<LlamaAM> getLlamaAMs() throws LlamaAMException {
    synchronized (ams) {
      return new HashSet<LlamaAM>(ams.values());
    }
  }


  @Override
  public void start() throws LlamaAMException {
    for (String queue :
        getConf().getTrimmedStringCollection(INITIAL_QUEUES_KEY)) {
      try {
        getLlamaAM(queue);
      } catch (LlamaAMException ex) {
        stop();
        throw ex;
      }
    }
    llamaAMForGetNodes = new SingleQueueLlamaAM(getConf(), null, this);
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
  public List<String> getNodes() throws LlamaAMException {
    return llamaAMForGetNodes.getNodes();
  }

  @SuppressWarnings("deprecation")
  @Override
  public PlacedReservation reserve(UUID reservationId, Reservation reservation)
      throws LlamaAMException {
    LlamaAM am = getLlamaAM(reservation.getQueue());
    PlacedReservation pr = am.reserve(reservationId, reservation);
    reservationToQueue.put(reservationId, reservation.getQueue());
    return pr;
  }

  @SuppressWarnings("deprecation")
  @Override
  public PlacedReservation getReservation(UUID reservationId)
      throws LlamaAMException {
    PlacedReservation reservation = null;
    String queue = reservationToQueue.get(reservationId);
    if (queue != null) {
      LlamaAM am = getLlamaAM(queue);
      reservation = am.getReservation(reservationId);
    } else {
      getLog().warn("getReservation({}), reservationId not found",
          reservationId);
    }
    return reservation;
  }

  @SuppressWarnings("deprecation")
  @Override
  public PlacedReservation releaseReservation(UUID handle, UUID reservationId) throws LlamaAMException {
    PlacedReservation pr = null;
    String queue = reservationToQueue.remove(reservationId);
    if (queue != null) {
      LlamaAM am = getLlamaAM(queue);
      pr = am.releaseReservation(handle, reservationId);
    } else {
      getLog().warn("releaseReservation({}), reservationId not found",
          reservationId);
    }
    return pr;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForHandle(UUID handle)
      throws LlamaAMException {
    List<PlacedReservation> reservations = new ArrayList<PlacedReservation>();
    LlamaAMException thrown = null;
    for (LlamaAM am : getLlamaAMs()) {
      try {
        reservations.addAll(am.releaseReservationsForHandle(handle));
      } catch (LlamaAMException ex) {
        if (thrown != null) {
          getLog().error("releaseReservationsForHandle({}), error: {}",
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
  public void discardAM(String queue) {
    getLog().warn("discarding queue '{}' and all its reservations", queue);
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
