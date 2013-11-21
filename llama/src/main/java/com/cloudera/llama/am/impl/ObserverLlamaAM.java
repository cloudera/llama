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
import com.cloudera.llama.am.api.LlamaAMObserver;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.server.MetricUtil;
import com.cloudera.llama.util.Clock;
import com.cloudera.llama.util.ParamChecker;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ObserverLlamaAM extends LlamaAM implements LlamaAMListener,
    Runnable {
  private final static String QUEUE_GAUGE = LlamaAM.METRIC_PREFIX +
      ".observer.queue.size.gauge";

  private final LlamaAM llamaAM;
  private final BlockingQueue<PlacedReservation> changes;
  private final Thread processorThread;
  private final LlamaAMObserver observer;

  public ObserverLlamaAM(LlamaAM llamaAM, LlamaAMObserver observer) {
    super(llamaAM.getConf());
    this.llamaAM = ParamChecker.notNull(llamaAM, "llamaAM");
    this.observer = ParamChecker.notNull(observer, "observer");
    changes = new LinkedBlockingQueue<PlacedReservation>();
    llamaAM.addListener(this);
    processorThread = new Thread(this, "llama-am-observer-processor");
    processorThread.setDaemon(true);
  }

  @Override
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    super.setMetricRegistry(metricRegistry);
    llamaAM.setMetricRegistry(metricRegistry);
    if (metricRegistry != null) {
      MetricUtil.registerGauge(metricRegistry, QUEUE_GAUGE,
          new Gauge<Integer>() {
            @Override
            public Integer getValue() {
              return changes.size();
            }
          });
    }
  }

  @Override
  public synchronized void start() throws LlamaAMException {
    llamaAM.start();
    processorThread.start();
  }

  @Override
  public synchronized void stop() {
    llamaAM.stop();
    processorThread.interrupt();
  }

  @Override
  public boolean isRunning() {
    return llamaAM.isRunning();
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    return llamaAM.getNodes();
  }

  @Override
  public void addListener(LlamaAMListener listener) {
    llamaAM.addListener(listener);
  }

  @Override
  public void removeListener(LlamaAMListener listener) {
    llamaAM.removeListener(listener);
  }

  @Override
  public PlacedReservation reserve(UUID reservationId, Reservation reservation)
      throws LlamaAMException {
    PlacedReservation pr = llamaAM.reserve(reservationId, reservation);
    changes.add(pr);
    return pr;
  }

  @Override
  public PlacedReservation getReservation(UUID reservationId)
      throws LlamaAMException {
    PlacedReservation reservation = llamaAM.getReservation(reservationId);
    return reservation;
  }

  @Override
  public PlacedReservation releaseReservation(UUID handle, UUID reservationId)
      throws LlamaAMException {
    PlacedReservation pr = llamaAM.releaseReservation(handle, reservationId);
    if (pr != null) {
      changes.add(pr);
    }
    return pr;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForHandle(UUID handle)
      throws LlamaAMException {
    List<PlacedReservation> ids = llamaAM.releaseReservationsForHandle(handle);
    changes.addAll(ids);
    return ids;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForQueue(String queue)
      throws LlamaAMException {
    List<PlacedReservation> ids = llamaAM.releaseReservationsForQueue(queue);
    changes.addAll(ids);
    return ids;
  }

  @Override
  public void handle(LlamaAMEvent event) {
    changes.addAll(event.getChanges());
  }

  @Override
  public void run() {
      try {
        List<PlacedReservation> list = new ArrayList<PlacedReservation>();
        while (llamaAM.isRunning()) {
          Clock.sleep(50);
          if (changes.peek() != null) {
            changes.drainTo(list, 500);
            while (!list.isEmpty()) {
              observer.observe(list);
              list.clear();
              changes.drainTo(list, 500);
            }
          }
        }
      } catch (InterruptedException ex) {
        //NOP
      }
  }

}
