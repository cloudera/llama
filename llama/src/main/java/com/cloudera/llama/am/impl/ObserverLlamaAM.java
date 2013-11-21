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
import com.cloudera.llama.util.ParamChecker;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.MetricRegistry;

import java.util.List;

public class ObserverLlamaAM extends LlamaAMImpl implements LlamaAMListener {

  private final LlamaAM llamaAM;

  public ObserverLlamaAM(LlamaAM llamaAM) {
    super(llamaAM.getConf());
    this.llamaAM = ParamChecker.notNull(llamaAM, "llamaAM");
    llamaAM.addListener(this);
  }

  @Override
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    llamaAM.setMetricRegistry(metricRegistry);
  }

  @Override
  public synchronized void start() throws LlamaAMException {
    llamaAM.start();
  }

  @Override
  public synchronized void stop() {
    llamaAM.stop();
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
  public PlacedReservation reserve(UUID reservationId, Reservation reservation)
      throws LlamaAMException {
    PlacedReservation pr = llamaAM.reserve(reservationId, reservation);
    LlamaAMEventImpl event = new LlamaAMEventImpl(true);
    event.addReservation(pr);
    dispatch(event);
    return pr;
  }

  @Override
  public PlacedReservation getReservation(UUID reservationId)
      throws LlamaAMException {
    return llamaAM.getReservation(reservationId);
  }

  @Override
  public PlacedReservation releaseReservation(UUID handle, UUID reservationId)
      throws LlamaAMException {
    PlacedReservation pr = llamaAM.releaseReservation(handle, reservationId);
    if (pr != null) {
      LlamaAMEventImpl event = new LlamaAMEventImpl(!handle.equals(ADMIN_HANDLE));
      event.addReservation(pr);
      dispatch(event);
    }
    return pr;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForHandle(UUID handle)
      throws LlamaAMException {
    List<PlacedReservation> prs = llamaAM.releaseReservationsForHandle(handle);
    LlamaAMEventImpl event = new LlamaAMEventImpl(false);
    for (PlacedReservation pr : prs) {
      event.addReservation(pr);
    }
    dispatch(event);
    return prs;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForQueue(String queue)
      throws LlamaAMException {
    List<PlacedReservation> prs = llamaAM.releaseReservationsForQueue(queue);
    LlamaAMEventImpl event = new LlamaAMEventImpl(false);
    for (PlacedReservation pr : prs) {
      event.addReservation(pr);
    }
    dispatch(event);
    return prs;
  }

  @Override
  public void onEvent(LlamaAMEvent event) {
    dispatch(event);
  }

}
