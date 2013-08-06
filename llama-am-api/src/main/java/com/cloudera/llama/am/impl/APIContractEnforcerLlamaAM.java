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
import com.cloudera.llama.am.LlamaAMException;
import com.cloudera.llama.am.LlamaAMListener;
import com.cloudera.llama.am.PlacedReservation;
import com.cloudera.llama.am.Reservation;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class APIContractEnforcerLlamaAM extends LlamaAM {
  private final Logger logger;
  private final LlamaAM llamaAM;
  private volatile boolean running;
  private volatile boolean stopped;

  public APIContractEnforcerLlamaAM(LlamaAM llamaAM) {
    this.llamaAM = ParamChecker.notNull(llamaAM, "llamaAM");
    logger = LoggerFactory.getLogger(this.llamaAM.getClass());
    running = false;
  }

  private Logger getLog() {
    return logger;
  }

  private void checkIsRunning() {
    if (!running) {
      throw new IllegalStateException("LlamaAM is not running");
    }
  }

  @Override
  public Configuration getConf() {
    return llamaAM.getConf();
  }

  @Override
  public synchronized void start() throws LlamaAMException {
    if (running) {
      throw new IllegalStateException("LlamaAM already running");
    }
    if (stopped) {
      throw new IllegalStateException("LlamaAM stopped, cannot be restarted");
    }
    llamaAM.start();
    getLog().trace("start()");
    running = true;
  }

  @Override
  public synchronized void stop() {
    if (running) {
      llamaAM.stop();
      getLog().trace("stop()");
      running = false;
      stopped = true;
    }
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    checkIsRunning();
    getLog().trace("getNodes()");
    return llamaAM.getNodes();
  }

  @Override
  public void addListener(LlamaAMListener listener) {
    checkIsRunning();
    ParamChecker.notNull(listener, "listener");
    llamaAM.addListener(listener);
    getLog().trace("addListener({})", listener);
  }

  @Override
  public void removeListener(LlamaAMListener listener) {
    checkIsRunning();
    ParamChecker.notNull(listener, "listener");
    llamaAM.removeListener(listener);
    getLog().trace("removeListener({})", listener);
  }

  @Override
  public UUID reserve(Reservation reservation) throws LlamaAMException {
    checkIsRunning();
    ParamChecker.notNull(reservation, "reservation");
    UUID reservationId = llamaAM.reserve(reservation);
    if (reservationId == null) {
      throw new IllegalStateException("Internal error, cannot return NULL");
    }
    getLog().trace("reserve({}): {}", reservation, reservationId);
    return reservationId;
  }

  @Override
  public PlacedReservation getReservation(UUID reservationId)
      throws LlamaAMException {
    checkIsRunning();
    ParamChecker.notNull(reservationId, "reservationId");
    PlacedReservation reservation = llamaAM.getReservation(reservationId);
    getLog().trace("getReservation({}): {}", reservationId, reservation);
    return reservation;
  }

  @Override
  public void releaseReservation(UUID reservationId) throws LlamaAMException {
    checkIsRunning();
    ParamChecker.notNull(reservationId, "reservationId");
    llamaAM.releaseReservation(reservationId);
    getLog().trace("releaseReservation({})", reservationId);
  }

  @Override
  public void releaseReservationsForClientId(UUID clientId)
      throws LlamaAMException {
    checkIsRunning();
    ParamChecker.notNull(clientId, "clientId");
    llamaAM.releaseReservationsForClientId(clientId);
    getLog().trace("releaseReservationsForClientId({})", clientId);
  }
}
