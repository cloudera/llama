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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class APIContractEnforcerLlamaAM extends LlamaAM {
  private LlamaAM llamaAM;
  private Logger logger;
  private boolean active;

  public APIContractEnforcerLlamaAM(LlamaAM llamaAM) {
    this.llamaAM = ParamChecker.notNull(llamaAM, "llamaAM");
    logger = LoggerFactory.getLogger(this.llamaAM.getClass());
    active = false;
  }

  private Logger getLog() {
    return logger;
  }

  private void checkIsActive() {
    if (!active) {
      throw new IllegalStateException("LlamaAM is not active");
    }
  }

  @Override
  public synchronized void start() throws LlamaAMException {
    if (active) {
      throw new IllegalStateException("LlamaAM already active");
    }
    llamaAM.start();
    getLog().trace("start()");
    active = true;
  }

  @Override
  public synchronized void stop() {
    if (active) {
      llamaAM.stop();
      getLog().trace("stop()");
      active = false;
    }
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    checkIsActive();
    getLog().trace("getNodes()");
    return llamaAM.getNodes();
  }

  @Override
  public void addListener(LlamaAMListener listener) {
    checkIsActive();
    ParamChecker.notNull(listener, "listener");
    llamaAM.addListener(listener);
    getLog().trace("addListener({})", listener);
  }

  @Override
  public void removeListener(LlamaAMListener listener) {
    checkIsActive();
    ParamChecker.notNull(listener, "listener");
    llamaAM.removeListener(listener);
    getLog().trace("removeListener({})", listener);
  }

  @Override
  public UUID reserve(Reservation reservation) throws LlamaAMException {
    checkIsActive();
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
    checkIsActive();
    ParamChecker.notNull(reservationId, "reservationId");
    PlacedReservation reservation = llamaAM.getReservation(reservationId);
    getLog().trace("getReservation({}): {}", reservationId, reservation);
    return reservation;
  }

  @Override
  public void releaseReservation(UUID reservationId) throws LlamaAMException {
    checkIsActive();
    ParamChecker.notNull(reservationId, "reservationId");
    llamaAM.releaseReservation(reservationId);
    getLog().trace("releaseReservation({})", reservationId);
  }

  @Override
  public void releaseReservationsForClientId(UUID clientId)
      throws LlamaAMException {
    checkIsActive();
    ParamChecker.notNull(clientId, "clientId");
    llamaAM.releaseReservationsForClientId(clientId);
    getLog().trace("releaseReservationsForClientId({})", clientId);
  }
}
