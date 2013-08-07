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
import com.cloudera.llama.am.PlacedResource;
import com.cloudera.llama.am.Reservation;
import com.cloudera.llama.am.Resource;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractSingleQueueLlamaAM extends LlamaAM implements 
    Configurable {
  static final String QUEUE_KEY = PREFIX_KEY + "single.queue";

  private final Logger logger;
  private Configuration conf;
  private LlamaAMListener listener;
  private final Map<UUID, PlacedReservationImpl> reservationsMap;
  private final Map<UUID, PlacedResourceImpl> resourcesMap;

  public AbstractSingleQueueLlamaAM() {
    logger = LoggerFactory.getLogger(getClass());
    reservationsMap = new HashMap<UUID, PlacedReservationImpl>();
    resourcesMap = new HashMap<UUID, PlacedResourceImpl>();
  }

  protected Logger getLog() {
    return logger;
  }

  // Configurable API

  @Override
  public Configuration getConf() {
    return conf;
  }

  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  // LlamaAM API

  @Override
  public void start() throws LlamaAMException {
    String queue = getConf().get(QUEUE_KEY);
    if (queue == null) {
      throw new IllegalStateException("Missing '" + QUEUE_KEY +
          "' configuration property");
    }
    rmRegister(queue);
  }

  @Override
  public synchronized void stop() {
    rmUnregister();
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    return rmGetNodes();
  }

  @Override
  public void addListener(LlamaAMListener listener) {
    this.listener = listener;
  }

  @Override
  public void removeListener(LlamaAMListener listener) {
    if (this.listener == listener) {
      this.listener = null;
    }
  }

  protected LlamaAMListener getListener() {
    return listener;
  }

  private void _addReservation(PlacedReservationImpl reservation) {
    UUID reservationId = reservation.getReservationId();
    reservationsMap.put(reservationId, reservation);
    for (PlacedResourceImpl resource : reservation.getResourceImpls()) {
      resource.setStatus(PlacedResource.Status.PENDING);
      UUID clientResourceId = resource.getClientResourceId();
      resourcesMap.put(clientResourceId, resource);
    }
  }

  PlacedReservationImpl _getReservation(UUID reservationId) {
    return reservationsMap.get(reservationId);
  }

  private PlacedReservationImpl _deleteReservation(UUID reservationId) {
    PlacedReservationImpl reservation = reservationsMap.remove(reservationId);
    if (reservation != null) {
      for (Resource resource : reservation.getResources()) {
        resourcesMap.remove(resource.getClientResourceId());
      }
    }
    return reservation;
  }

  @Override
  public UUID reserve(final Reservation reservation) throws LlamaAMException {
    final PlacedReservationImpl impl = new PlacedReservationImpl(reservation);
    rmReserve(impl);
    synchronized (this) {
      _addReservation(impl);
    }
    return impl.getReservationId();
  }

  @Override
  public PlacedReservation getReservation(final UUID reservationId)
      throws LlamaAMException {
    synchronized (this) {
      return _getReservation(reservationId);
    }
  }

  @Override
  public void releaseReservation(final UUID reservationId) throws LlamaAMException {
    PlacedReservationImpl reservation;
    synchronized (this) {
      reservation = _deleteReservation(reservationId);
    }
    if (reservation != null) {
      rmRelease(reservation.getResources());
    } else {
      getLog().warn("Unknown reservationId '{}'", reservationId);
    }
  }

  @Override
  public void releaseReservationsForClientId(UUID clientId)
      throws LlamaAMException {
    List<PlacedReservation> reservations = new ArrayList<PlacedReservation>();
    synchronized (this) {
      for (PlacedReservation reservation :
          new ArrayList<PlacedReservation>(reservationsMap.values())) {
        if (reservation.getClientId().equals(clientId)) {
          _deleteReservation(reservation.getReservationId());
          reservations.add(reservation);
        }
      }
    }
    for (PlacedReservation reservation : reservations) {
      rmRelease(reservation.getResources());
    }
  }

  private LlamaAMEventImpl getEventForClientId(Map<UUID,
      LlamaAMEventImpl> eventsMap, UUID clientId) {
    LlamaAMEventImpl event = eventsMap.get(clientId);
    if (event == null) {
      event = new LlamaAMEventImpl(clientId);
      eventsMap.put(clientId, event);
    }
    return event;
  }

  private List<PlacedResource> _resourceRejected(PlacedResourceImpl resource,
      Map<UUID, LlamaAMEventImpl> eventsMap) {
    List<PlacedResource> toRelease = null;
    resource.setStatus(PlacedResource.Status.REJECTED);
    UUID reservationId = resource.getReservationId();
    PlacedReservationImpl reservation = reservationsMap.get(reservationId);
    if (reservation == null) {
      getLog().warn("Unknown Reservation '{}' during resource '{}' rejection " + 
          "handling", reservationId, resource.getClientResourceId());
    } else {
      LlamaAMEventImpl event = getEventForClientId(eventsMap,
          reservation.getClientId());
      // if reservation is ALLOCATED, or it is PARTIAL and not GANG we let it be
      // and in the ELSE we notify the resource rejection
      switch (reservation.getStatus()) {
        case PENDING:
        case PARTIAL:
          if (reservation.isGang()) {
            _deleteReservation(reservationId);
            toRelease = reservation.getResources();
            event.getRejectedReservationIds().add(reservationId);
          }
          event.getRejectedClientResourcesIds().add(resource
              .getClientResourceId());
          break;
        case ALLOCATED:
          logger.warn("Illegal internal state, reservation '{}' is " +
              "ALLOCATED, resource cannot  be rejected '{}'",
              reservationId, resource.getClientResourceId());
          break;
      }
    }
    return toRelease;
  }

  private void _resourceAllocated(PlacedResourceImpl resource,
      RMResourceChange change, Map<UUID, LlamaAMEventImpl> eventsMap) {
    resource.setAllocationInfo(change.getvCpuCores(), change.getMemoryMb(), 
        change.getLocation(), change.getRmResourceId());
    UUID reservationId = resource.getReservationId();
    PlacedReservationImpl reservation = reservationsMap.get(reservationId);
    if (reservation == null) {
      getLog().warn("Reservation '{}' during resource allocation handling " +
          "for" + " '{}'", reservationId, resource.getClientResourceId());
    } else {
      LlamaAMEventImpl event = getEventForClientId(eventsMap,
          reservation.getClientId());
      List<PlacedResourceImpl> resources = reservation.getResourceImpls();
      boolean fulfilled = true;
      for (int i = 0; fulfilled && i < resources.size(); i++) {
        fulfilled = resources.get(i).getStatus() == PlacedResource.Status
            .ALLOCATED;
      }
      if (fulfilled) {
        reservation.setStatus(PlacedReservation.Status.ALLOCATED);
        event.getAllocatedReservationIds().add(reservationId);
        if (reservation.isGang()) {
          event.getAllocatedResources().addAll(reservation.getResourceImpls());
        } else {
          event.getAllocatedResources().add(resource);          
        }
      } else {
        reservation.setStatus(PlacedReservation.Status.PARTIAL);
        if (!reservation.isGang()) {
          event.getAllocatedResources().add(resource);
        }
      }
    }
  }

  private List<PlacedResource> _resourcePreempted(PlacedResourceImpl resource,
      Map<UUID, LlamaAMEventImpl> eventsMap) {
    List<PlacedResource> toRelease = null;
    resource.setStatus(PlacedResource.Status.PREEMPTED);
    UUID reservationId = resource.getReservationId();
    PlacedReservationImpl reservation = reservationsMap.get(reservationId);
    if (reservation == null) {
      getLog().warn("Unknown Reservation '{}' during resource preemption " +
          "handling for" + " '{}'", reservationId, 
          resource.getClientResourceId());
    } else {
      LlamaAMEventImpl event = getEventForClientId(eventsMap,
          reservation.getClientId());
      switch (reservation.getStatus()) {
        case ALLOCATED:
          event.getPreemptedClientResourceIds().add(
              resource.getClientResourceId());
          break;
        case PARTIAL:
          if (reservation.isGang()) {
            _deleteReservation(reservationId);
            toRelease = reservation.getResources();
            event.getRejectedReservationIds().add(reservationId);
          } else {
            event.getPreemptedClientResourceIds().add(
                resource.getClientResourceId());
          }
          break;
        case PENDING:
          logger.warn("Illegal internal state, reservation '{}' is " +
              "PENDING, resource cannot  be preempted '{}', deleting " +
              "reservation", reservationId, resource.getClientResourceId());
          _deleteReservation(reservationId);
          toRelease = reservation.getResources();
          event.getRejectedReservationIds().add(reservationId);
          break;
      }
    }
    return toRelease;
  }

  private List<PlacedResource> _resourceLost(PlacedResourceImpl resource,
      Map<UUID, LlamaAMEventImpl> eventsMap) {
    List<PlacedResource> toRelease = null;
    resource.setStatus(PlacedResource.Status.LOST);
    UUID reservationId = resource.getReservationId();
    PlacedReservationImpl reservation = reservationsMap.get(reservationId);
    if (reservation == null) {
      getLog().warn("Unknown Reservation '{}' during resource lost handling " +
          "for '{}'", reservationId, resource.getClientResourceId());
    } else {
      LlamaAMEventImpl event = getEventForClientId(eventsMap,
          reservation.getClientId());
      switch (reservation.getStatus()) {
        case ALLOCATED:
          event.getLostClientResourcesIds().add(resource.getClientResourceId());
          break;
        case PARTIAL:
          if (reservation.isGang()) {
            _deleteReservation(reservationId);
            toRelease = reservation.getResources();
            event.getRejectedReservationIds().add(reservationId);
          } else {
            event.getLostClientResourcesIds().add(resource
                .getClientResourceId());
          }
          break;
        case PENDING:
          throw new IllegalStateException(FastFormat.format("Reservation "
              + "'{}' in pending, cannot have a lost resource '{}'", 
              reservationId, resource.getClientResourceId()));
      }
    }
    return toRelease;
  }

  // API to wire with RM client

  protected void rmChanges(final List<RMResourceChange> changes) {
    if (changes == null) {
      throw new IllegalArgumentException("changes cannot be NULL");
    }
    final boolean hasListener = listener != null;
    getLog().trace("rmChanges({})", changes);
    Map<UUID, LlamaAMEventImpl> eventsMap = 
        new HashMap<UUID, LlamaAMEventImpl>();
    List<PlacedResource> toRelease = new ArrayList<PlacedResource>();
    synchronized (this) {
      for (RMResourceChange change : changes) {
        PlacedResourceImpl resource = resourcesMap.get(change
            .getClientResourceId());
        if (resource == null) {
          getLog().warn("Unknown resource '{}'",
              change.getClientResourceId());
        } else {
          List<PlacedResource> release = null;
          switch (change.getStatus()) {
            case REJECTED:
              release = _resourceRejected(resource, eventsMap);
              break;
            case ALLOCATED:
              _resourceAllocated(resource, change, eventsMap);
              break;
            case PREEMPTED:
              toRelease =_resourcePreempted(resource, eventsMap);
              break;
            case LOST:
              release = _resourceLost(resource, eventsMap);
              break;
          }
          if (release != null) {
            toRelease.addAll(release);
          }
        }
      }
    }
    if (toRelease != null) {
      try {
        rmRelease(toRelease);
      } catch (LlamaAMException ex) {
        getLog().warn("rmRelease() error: {}", ex.toString(), ex);
      }
    }  
    if (hasListener && !eventsMap.isEmpty()) {
        for (LlamaAMEventImpl event : eventsMap.values()) {
          try {
            if (!event.isEmpty()) {
              listener.handle(event);
            }
          } catch (Throwable ex) {
            getLog().warn("listener.handle() error: {}", ex.toString(), ex);
          }
        }
    }
  }

  protected abstract void rmRegister(String queue) throws LlamaAMException;

  protected abstract void rmUnregister();

  protected abstract List<String> rmGetNodes() throws LlamaAMException;
  
  protected abstract void rmReserve(PlacedReservation reservation)
      throws LlamaAMException;

  protected abstract void rmRelease(Collection<PlacedResource> resources)
      throws LlamaAMException;

}
