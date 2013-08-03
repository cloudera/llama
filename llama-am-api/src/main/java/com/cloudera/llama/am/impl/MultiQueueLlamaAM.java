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
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MultiQueueLlamaAM extends LlamaAM implements Configurable, 
    LlamaAMListener {

  private static Logger LOG = LoggerFactory.getLogger(MultiQueueLlamaAM.class);

  public static final String PREFIX_KEY = LlamaAM.PREFIX_KEY + "multi.";
  public static final String MULTI_CREATE_KEY = PREFIX_KEY + "create";
  public static final String SINGLE_QUEUE_AM_CLASS_KEY = PREFIX_KEY + 
      "single.am.class";

  private Configuration conf;
  private final Map<String, LlamaAM> ams;
  private final ConcurrentHashMap<UUID, String> reservationToQueue;
  private final Set<LlamaAMListener> listeners;

  public MultiQueueLlamaAM() {
    ams = new HashMap<String, LlamaAM>();
    listeners = new HashSet<LlamaAMListener>();
    reservationToQueue = new ConcurrentHashMap<UUID, String>();
  }

  // Configurable API

  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
    conf.setBoolean(MULTI_CREATE_KEY, false);
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  // LlamaAMListener API

  @Override
  public void handle(LlamaAMEvent event) {
    synchronized (listeners) {
      for (LlamaAMListener al : listeners) {
        al.handle(event);
      }
    }
  }

  // LlamaAM API

  private LlamaAM getLlamaAM(String queue) throws LlamaAMException {
    LlamaAM am;
    //TODO: see how to remove global contention during creation/start of an AM
    synchronized (ams) {
      am = ams.get(queue);
      if (am == null) {
        Configuration conf = new Configuration(getConf());
        conf.set(AbstractSingleQueueLlamaAM.QUEUE_KEY, queue);
        am = LlamaAM.create(conf);
        am.start();
        am.addListener(MultiQueueLlamaAM.this);
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
    for (String queue : conf.getTrimmedStringCollection(INITIAL_QUEUES_KEY)) {
      try {
        getLlamaAM(queue);
      } catch (LlamaAMException ex) {
        stop();
        throw ex;
      }
    }
  }

  @Override
  public void stop() {
    synchronized (ams) {
      for (LlamaAM am : ams.values()) {
        am.stop();
      }
    }
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    LlamaAM am = getAnyLlama();
    return am.getNodes();
  }

  @Override
  public void addListener(LlamaAMListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  @Override
  public void removeListener(LlamaAMListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public UUID reserve(Reservation reservation) throws LlamaAMException {
    LlamaAM am = getLlamaAM(reservation.getQueue());
    UUID reservationId = am.reserve(reservation);
    reservationToQueue.put(reservationId, reservation.getQueue());
    return reservationId;
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
      LOG.warn("getReservation({}), reservationId not found", reservationId);
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
      LOG.warn("releaseReservation({}), reservationId not found", reservationId);
    }
  }

  @Override
  public void releaseReservationsForClientId(UUID clientId)
      throws LlamaAMException {
    LlamaAMException thrown = null;
    for (LlamaAM am : getLlamaAMs()) {
      try {
        am.releaseReservationsForClientId(clientId);
      } catch (LlamaAMException ex) {
        if (thrown != null) {
          LOG.error("releaseReservationsFoClientId({}), error: {}", 
              new Object[]{clientId, ex.toString(), ex});
        }
        thrown = ex;
      }
    }
    if (thrown != null) {
      throw thrown;
    }
  }
}
