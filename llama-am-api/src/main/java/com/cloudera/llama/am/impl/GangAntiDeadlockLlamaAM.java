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
import com.cloudera.llama.am.PlacedResource;
import com.cloudera.llama.am.Reservation;
import org.apache.hadoop.conf.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class GangAntiDeadlockLlamaAM extends LlamaAMImpl implements
    LlamaAMListener, Runnable {

  static class BackedOffReservation implements Delayed {
    private PlacedReservation reservation;
    private long delayedUntil;

    public BackedOffReservation(PlacedReservation reservation, long delay) {
      this.reservation = reservation;
      this.delayedUntil = System.currentTimeMillis() +  delay;
    }

    public PlacedReservation getReservation() {
      return reservation;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(delayedUntil - System.currentTimeMillis(),
          TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
      return (int) (delayedUntil - ((BackedOffReservation)o).delayedUntil);
    }
  }

  private final LlamaAM am;

  //visible for testing
  Map<UUID, PlacedReservationImpl> localReservations;
  Set<UUID> submittedReservations;
  DelayQueue<BackedOffReservation> backedOffReservations;
  volatile long timeOfLastAllocation;

  private long noAllocationLimit;
  private int backOffPercent;
  private long backOffMinDelay;
  private long backOffMaxDelay;
  private Random random;

  public GangAntiDeadlockLlamaAM(Configuration conf, LlamaAM llamaAM) {
    super(conf);
    this.am = llamaAM;
    am.addListener(this);
  }

  @Override
  public void start() throws LlamaAMException {
    am.start();
    localReservations = new HashMap<UUID, PlacedReservationImpl>();
    submittedReservations = new HashSet<UUID>();
    backedOffReservations = new DelayQueue<BackedOffReservation>();
    noAllocationLimit = getConf().getLong(
        GANG_ANTI_DEADLOCK_NO_ALLOCATION_LIMIT_KEY,
        GANG_ANTI_DEADLOCK_NO_ALLOCATION_LIMIT_DEFAULT);
    backOffPercent = getConf().getInt(
        GANG_ANTI_DEADLOCK_BACKOFF_PERCENT_KEY,
        GANG_ANTI_DEADLOCK_BACKOFF_PERCENT_DEFAULT);
    backOffMinDelay = getConf().getLong(
        GANG_ANTI_DEADLOCK_BACKOFF_MIN_DELAY_KEY,
        GANG_ANTI_DEADLOCK_BACKOFF_MIN_DELAY_DEFAULT);
    backOffMaxDelay = getConf().getLong(
        GANG_ANTI_DEADLOCK_BACKOFF_MAX_DELAY_KEY,
        GANG_ANTI_DEADLOCK_BACKOFF_MAX_DELAY_DEFAULT);
    random = new Random();
    timeOfLastAllocation = System.currentTimeMillis();
    startDeadlockResolverThread();
    am.addListener(this);
  }

  //visible for testing
  void startDeadlockResolverThread() {
    Thread deadlockResolverThread = new Thread(this);
    deadlockResolverThread.setDaemon(true);
    deadlockResolverThread.setName("GangAntiDeadlockLlamaAM");
    deadlockResolverThread.start();
  }

  @Override
  public void stop() {
    am.stop();
  }

  @Override
  public boolean isRunning() {
    return am.isRunning();
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    return am.getNodes();
  }

  @Override
  public void reserve(UUID reservationId, Reservation reservation)
      throws LlamaAMException {
    if (reservation.isGang()) {
      PlacedReservationImpl placedReservation =
          new PlacedReservationImpl(reservationId, reservation);
      gReserve(reservationId, placedReservation);
    }
    am.reserve(reservationId, reservation);
  }

  private synchronized void gReserve(UUID reservationId,
      PlacedReservationImpl placedReservation) {
    localReservations.put(reservationId, placedReservation);
    if (backedOffReservations.isEmpty()) {
      submittedReservations.add(reservationId);
    } else {
      backedOffReservations.add(new BackedOffReservation(placedReservation,
          getBackOffDelay()));
    }
  }

  @Override
  public PlacedReservation getReservation(UUID reservationId)
      throws LlamaAMException {
    PlacedReservation pr = am.getReservation(reservationId);
    if (pr == null) {
      pr = gGetReservation(reservationId);
    }
    return pr;
  }

  private synchronized PlacedReservation gGetReservation(UUID reservationId) {
    return localReservations.get(reservationId);
  }

  @Override
  public void releaseReservation(UUID reservationId) throws LlamaAMException {
    gReleaseReservation(reservationId);
    am.releaseReservation(reservationId);
  }

  private synchronized void gReleaseReservation(UUID reservationId) {
    localReservations.remove(reservationId);
    submittedReservations.remove(reservationId);
  }

  @Override
  public List<UUID> releaseReservationsForClientId(UUID clientId)
      throws LlamaAMException {
    List<UUID> ids = am.releaseReservationsForClientId(clientId);
    ids.addAll(gReleaseReservationsForClientId(clientId));
    return new ArrayList<UUID>(new HashSet<UUID>(ids));
  }

  private synchronized List<UUID> gReleaseReservationsForClientId(
      UUID clientId) {
    List<UUID> ids = new ArrayList<UUID>();
    Iterator<PlacedReservationImpl> it =
        localReservations.values().iterator();
    while (it.hasNext()) {
      PlacedReservation pr = it.next();
      if (pr.getClientId().equals(clientId)) {
        it.remove();
        submittedReservations.remove(pr.getReservationId());
        ids.add(pr.getReservationId());
      }
    }
    return ids;
  }

  @Override
  public void addListener(LlamaAMListener listener) {
    am.addListener(listener);
  }

  @Override
  public void removeListener(LlamaAMListener listener) {
    am.removeListener(listener);
  }

  @Override
  public synchronized void handle(LlamaAMEvent event) {
    LlamaAMEventImpl eventImpl = (LlamaAMEventImpl) event;
    for (PlacedResource resource : eventImpl.getAllocatedGangResources()) {
      if (submittedReservations.contains(resource.getReservationId())) {
        timeOfLastAllocation = System.currentTimeMillis();
        getLog().debug("Resetting last resource allocation");
        break;
      }
    }
    for (UUID id : event.getAllocatedReservationIds()) {
      localReservations.remove(id);
      submittedReservations.remove(id);
    }
    for (UUID id : event.getRejectedReservationIds()) {
      localReservations.remove(id);
      submittedReservations.remove(id);
    }
  }

  @Override
  public void run() {
    while (isRunning()) {
      try {
        long sleepTime1 = deadlockAvoidance();
        long sleepTime2 = reReserveBackOffs();
        long sleepTime = Math.min(sleepTime1, sleepTime2);
        Thread.sleep(sleepTime);
      } catch (InterruptedException ex) {
        //NOP
      }
    }
  }

  long deadlockAvoidance() {
    long sleepTime;
    long timeWithoutAllocations = System.currentTimeMillis() -
        timeOfLastAllocation;
    if (timeWithoutAllocations >= noAllocationLimit) {
      int resourcesBackedOff = doReservationsBackOff();
      getLog().info("Deadlock avoidance backed off {} resource reservations",
          resourcesBackedOff);
      sleepTime = noAllocationLimit;
    } else {
      getLog().debug("Recent allocation, skipping back off");
      sleepTime = timeWithoutAllocations;
    }
    return sleepTime;
  }

  long getBackOffDelay() {
    return backOffMinDelay + random.nextInt((int)
        (backOffMaxDelay - backOffMinDelay));
  }

  synchronized int doReservationsBackOff() {
    getLog().debug("Starting reservations back off");
    int numberOfGangResources = 0;
    List<UUID> submitted = new ArrayList<UUID>(submittedReservations);
    for (UUID id : submitted) {
      PlacedReservation pr = localReservations.get(id);
      if (pr != null) {
        numberOfGangResources += pr.getResources().size();
      }
    }
    int toGetRidOff = numberOfGangResources * backOffPercent / 100;
    int gotRidOff = 0;
    while (gotRidOff < toGetRidOff && !submitted.isEmpty()) {
      int victim = random.nextInt(submitted.size());
      UUID reservationId = submitted.get(victim);
      PlacedReservationImpl reservation =
          localReservations.get(reservationId);
      if (reservation != null) {
        try {
          getLog().debug("Backing off {}", reservation);
          am.releaseReservation(reservation.getReservationId());
          backedOffReservations.add(new BackedOffReservation(reservation,
              getBackOffDelay()));
          submittedReservations.remove(reservationId);
          submitted.remove(reservationId);
        } catch (LlamaAMException ex) {
          getLog().warn("Error while backing off: {}", ex.toString(), ex);
        }
        gotRidOff += reservation.getResources().size();
      }
    }
    getLog().debug("Finishing reservations back off");
    //resetting to current last allocation to start waiting again.
    timeOfLastAllocation = System.currentTimeMillis();
    return gotRidOff;
  }

  synchronized long reReserveBackOffs() {
    getLog().debug("Starting re-reservations for backed off reservations");
    BackedOffReservation br = backedOffReservations.poll();
    while (br != null) {
      UUID reservationId = br.getReservation().getReservationId();
      if (localReservations.containsKey(reservationId)) {
        try {
          getLog().debug("Re-reservation for {}", br.getReservation());
          am.reserve(reservationId, br.getReservation());
          submittedReservations.add(reservationId);
        } catch (LlamaAMException ex) {
          localReservations.remove(reservationId);
          LlamaAMEvent event = new LlamaAMEventImpl(br.getReservation().
              getClientId());
          event.getRejectedReservationIds().add(reservationId);
          dispatch(event);
        }
      }
      br = backedOffReservations.poll();
    }
    br = backedOffReservations.peek();
    getLog().debug("Finishing re-reservations for backed off reservations");
    return (br != null) ? br.getDelay(TimeUnit.MILLISECONDS) : Long.MAX_VALUE;
  }
}
