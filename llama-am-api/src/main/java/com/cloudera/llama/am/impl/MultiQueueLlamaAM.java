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

import com.cloudera.llama.am.LlamaAM;
import com.cloudera.llama.am.LlamaAMEvent;
import com.cloudera.llama.am.LlamaAMException;
import com.cloudera.llama.am.LlamaAMListener;
import com.cloudera.llama.am.PlacedReservation;
import com.cloudera.llama.am.Reservation;
import org.apache.hadoop.conf.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MultiQueueLlamaAM extends LlamaAMImpl implements LlamaAMListener,
  SingleQueueLlamaAM.Callback {
  private final Map<String, LlamaAM> ams;
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
  
  private LlamaAM getAnyLlama() throws LlamaAMException {
    LlamaAM am;
    synchronized (ams) {
      Iterator<Map.Entry<String, LlamaAM>> iterator = ams.entrySet().iterator();
      if (iterator.hasNext()) {
        am = iterator.next().getValue();
      } else {
        throw new LlamaAMException("There is not active LlamaAM for any queue");
      }
    }
    return am;    
  }

  @Override
  public void start() throws LlamaAMException {
    for (String queue : getConf().getTrimmedStringCollection(INITIAL_QUEUES_KEY)) {
      try {
        getLlamaAM(queue);
      } catch (LlamaAMException ex) {
        stop();
        throw ex;
      }
    }
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
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    LlamaAM am = getAnyLlama();
    return am.getNodes();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void reserve(UUID reservationId, Reservation reservation)
      throws LlamaAMException {
    LlamaAM am = getLlamaAM(reservation.getQueue());
    am.reserve(reservationId, reservation);
    reservationToQueue.put(reservationId, reservation.getQueue());
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
  public void releaseReservation(UUID reservationId) throws LlamaAMException {
    String queue = reservationToQueue.remove(reservationId);
    if (queue != null) {
      LlamaAM am = getLlamaAM(queue);
      am.releaseReservation(reservationId);
    } else {
      getLog().warn("releaseReservation({}), reservationId not found",
          reservationId);
    }
  }

  @Override
  public List<UUID> releaseReservationsForClientId(UUID clientId)
      throws LlamaAMException {
    List<UUID> ids = new ArrayList<UUID>();
    LlamaAMException thrown = null;
    for (LlamaAM am : getLlamaAMs()) {
      try {
        ids.addAll(am.releaseReservationsForClientId(clientId));
      } catch (LlamaAMException ex) {
        if (thrown != null) {
          getLog().error("releaseReservationsFoClientId({}), error: {}",
              clientId, ex.toString(), ex);
        }
        thrown = ex;
      }
    }
    if (thrown != null) {
      throw thrown;
    }
    return ids;
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
