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

import com.cloudera.llama.am.api.Expansion;
import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.NodeInfo;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.util.ParamChecker;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.MetricRegistry;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The <code>APIContractLlamaAM</code> is a wrapper that enforces precondition
 * checks for all {@link LlamaAM} methods, both state of the
 * <code>LlamaAM</code> instance and the parameter values. It also logs
 * (<code>TRACE</code> level) all invocations to the <code>LlamaAM</code>.
 * <p/>
 * The {@link LlamaAM#create(Configuration)} wraps all created instances with
 * a <code>APIContractLlamaAM</code>.
 * <p/>
 * This implementation does not have configuration knobs.
*/
public class APIContractLlamaAM extends LlamaAM {
  private static final Logger LOG =
      LoggerFactory.getLogger(APIContractLlamaAM.class);
  private final LlamaAM llamaAM;
  private volatile boolean stopped;

  public APIContractLlamaAM(LlamaAM llamaAM) {
    super(llamaAM.getConf());
    this.llamaAM = llamaAM;
  }

  @Override
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    super.setMetricRegistry(metricRegistry);
    llamaAM.setMetricRegistry(metricRegistry);
  }

  private void checkIsRunning() {
    if (!llamaAM.isRunning()) {
      throw new IllegalStateException("LlamaAM is not running");
    }
  }

  @Override
  public Configuration getConf() {
    return llamaAM.getConf();
  }

  @Override
  public synchronized void start() throws LlamaException {
    if (llamaAM.isRunning()) {
      throw new IllegalStateException("LlamaAM already running");
    }
    if (stopped) {
      throw new IllegalStateException("LlamaAM stopped, cannot be restarted");
    }
    llamaAM.start();
    LOG.trace("start()");
  }

  @Override
  public synchronized void stop() {
    if (llamaAM.isRunning()) {
      llamaAM.stop();
      LOG.trace("stop()");
      stopped = true;
    }
  }

  @Override
  public boolean isRunning() {
    return llamaAM.isRunning();
  }

  @Override
  public List<NodeInfo> getNodes() throws LlamaException {
    checkIsRunning();
    LOG.trace("getNodes()");
    return llamaAM.getNodes();
  }

  @Override
  public void emptyCacheForQueue(String queue) throws LlamaException {
    checkIsRunning();
    ParamChecker.notNull(queue, "queue");
    llamaAM.emptyCacheForQueue(queue);
    LOG.trace("emptyCacheForQueue({})", queue);
  }

  @Override
  public void addListener(LlamaAMListener listener) {
    if (stopped) {
      throw new IllegalStateException("LlamaAM stopped");
    }
    ParamChecker.notNull(listener, "listener");
    llamaAM.addListener(listener);
    LOG.trace("addListener({})", listener);
  }

  @Override
  public void removeListener(LlamaAMListener listener) {
    if (stopped) {
      throw new IllegalStateException("LlamaAM stopped");
    }
    ParamChecker.notNull(listener, "listener");
    llamaAM.removeListener(listener);
    LOG.trace("removeListener({})", listener);
  }

  @Override
  public void reserve(UUID reservationId, Reservation reservation)
      throws LlamaException {
    LOG.trace("reserve({}): {}", reservation, reservationId);

    checkIsRunning();
    ParamChecker.notNull(reservationId, "reservationId");
    ParamChecker.notNull(reservation, "reservation");
    llamaAM.reserve(reservationId, reservation);
  }

  @Override
  public void expand(UUID expansionId, Expansion expansion)
      throws LlamaException {
    LOG.trace("expand({}): {}", expansion, expansionId);

    checkIsRunning();
    ParamChecker.notNull(expansionId, "reservationId");
    ParamChecker.notNull(expansion, "reservation");
    llamaAM.expand(expansionId, expansion);
  }

  @Override
  public PlacedReservation getReservation(UUID reservationId)
      throws LlamaException {
    checkIsRunning();
    ParamChecker.notNull(reservationId, "reservationId");
    PlacedReservation reservation = llamaAM.getReservation(reservationId);
    LOG.trace("getReservation({}): {}", reservationId, reservation);
    return reservation;
  }

  @Override
  public PlacedReservation releaseReservation(UUID handle, UUID reservationId,
      boolean doNotCache)
      throws LlamaException {
    LOG.trace("releaseReservation({}, {})", reservationId, doNotCache);
    checkIsRunning();
    ParamChecker.notNull(reservationId, "reservationId");
    ParamChecker.notNull(handle, "handle");
    PlacedReservation pr = llamaAM.releaseReservation(handle, reservationId,
        doNotCache);
    return pr;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForHandle(UUID handle,
      boolean doNotCache)
      throws LlamaException {
    LOG.trace("releaseReservationsForHandle({})", handle);

    checkIsRunning();
    ParamChecker.notNull(handle, "handle");
    List<PlacedReservation> ids = llamaAM.releaseReservationsForHandle(handle,
        doNotCache);
    return ids;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForQueue(
      String queue, boolean doNotCache) throws LlamaException {
    checkIsRunning();
    ParamChecker.notNull(queue, "queue");
    List<PlacedReservation> ids = llamaAM.releaseReservationsForQueue(queue,
        doNotCache);
    LOG.trace("releaseReservationsForQueue({})", queue);
    return ids;
  }

}
