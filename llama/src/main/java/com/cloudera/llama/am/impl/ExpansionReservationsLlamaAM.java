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
import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.NodeInfo;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.util.ErrorCode;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The <code>ExpansionReservationsLlamaAM</code> is a {@link LlamaAM} wrapper
 * that specializes in providing support for reservation expansions on top of
 * a LlamaAM providing only reservations support.
 * <p/>
 * This implementation does not have configuration knobs.
 */
public class ExpansionReservationsLlamaAM extends LlamaAMImpl
    implements LlamaAMListener {
  private static final Logger LOG = LoggerFactory.getLogger(
      ExpansionReservationsLlamaAM.class);

  private LlamaAM am;

  // Private class to store the expansion and the client handle pairs.
  // A completely different client can submit the expansion so we cannot rely on
  // the reservation client handle id.
  private static class ExpansionId {
    UUID expansionId;
    UUID handle;

    public ExpansionId(UUID expansionId, UUID handle) {
      this.expansionId = expansionId;
      this.handle = handle;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) { return true; }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      ExpansionId that = (ExpansionId) o;
      if (!expansionId.equals(that.expansionId)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return expansionId.hashCode();
    }
  }
  private Map<UUID, Set<ExpansionId>> reservationToExpansionsMap;

  private Map<UUID, Map<String, NodeInfo>> reservationTotalAsk;

  public ExpansionReservationsLlamaAM(LlamaAM am) {
    super(am.getConf());
    this.am = am;
    am.addListener(this);
    reservationToExpansionsMap = new HashMap<UUID, Set<ExpansionId>>();
    reservationTotalAsk = new HashMap<UUID, Map<String, NodeInfo>>();
  }

  @Override
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    super.setMetricRegistry(metricRegistry);
    am.setMetricRegistry(metricRegistry);
  }

  @Override
  public void start() throws LlamaException {
    am.start();
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
  public List<NodeInfo> getNodes() throws LlamaException {
    return am.getNodes();
  }

  @Override
  public void reserve(UUID reservationId, Reservation reservation)
      throws LlamaException {
    checkAndUpdateCapacity(reservationId, null, reservation.getResources(),
        _getNodes());
    am.reserve(reservationId, reservation);
  }

  private synchronized  void checkAndUpdateCapacity(UUID reservationId,
                   UUID expansionId,
                   List<Resource> askResources,
                   Map<String, NodeInfo> nodeToNodeInfo) throws LlamaException {
    if (askResources == null) {
      return;
    }

    Map<String, NodeInfo> currentNodesAskMap;
    if (expansionId != null) {
      currentNodesAskMap = reservationTotalAsk.get(reservationId);
    }  else {
      // This is a new reservation
      currentNodesAskMap = new HashMap<String, NodeInfo>();
    }

    for(Resource resource : askResources) {
      NodeInfo node = nodeToNodeInfo.get(resource.getLocationAsk());
      if (node == null) {
        throw new LlamaException(ErrorCode.RESERVATION_ASKING_UNKNOWN_NODE,
            reservationId, expansionId, resource.getLocationAsk());
      }

      int newCpusAsk = resource.getCpuVCoresAsk();
      int newMemoryAsk = resource.getMemoryMbsAsk();
      NodeInfo currentAsk = currentNodesAskMap.get(node.getLocation());
      if (currentAsk != null) {
        newCpusAsk += currentAsk.getCpusVCores();
        newMemoryAsk += currentAsk.getMemoryMB();
      }

      if (newCpusAsk > node.getCpusVCores()) {
        throw new LlamaException(ErrorCode.RESERVATION_ASKING_MORE_VCORES,
            reservationId, expansionId, newCpusAsk, node.getCpusVCores(), node.getLocation());
      }
      if (newMemoryAsk > node.getMemoryMB()) {
        throw new LlamaException(ErrorCode.RESERVATION_ASKING_MORE_MB,
            reservationId, expansionId, newMemoryAsk, node.getMemoryMB(), node.getLocation());
      }

      // Now update the currentNodesAskMap info.
      if (currentAsk == null) {
        currentAsk = new NodeInfo(resource.getLocationAsk());
        currentNodesAskMap.put(resource.getLocationAsk(), currentAsk);
      }
      currentAsk.setCpus(newCpusAsk);
      currentAsk.setMemoryMB(newMemoryAsk);
    }
    // Finally put it in the map.
    reservationTotalAsk.put(reservationId, currentNodesAskMap);
  }

  private Map<String, NodeInfo> _getNodes() throws LlamaException {
    Map<String, NodeInfo> nodeToNodeInfo = new HashMap<String, NodeInfo>();
    List<NodeInfo> nodes = getNodes();
    if (nodes != null) {
      for (NodeInfo node : nodes) {
        nodeToNodeInfo.put(node.getLocation(), node);
      }
    }
    return  nodeToNodeInfo;
  }

  synchronized boolean addExpansion(UUID reservationId,
                                    UUID expansionId, UUID handle) {
    if (reservationTotalAsk.get(reservationId) != null) {
      Set<ExpansionId> expansions = reservationToExpansionsMap.get(reservationId);
      if (expansions == null) {
        expansions = new HashSet<ExpansionId>();
        reservationToExpansionsMap.put(reservationId, expansions);
      }
      expansions.add(new ExpansionId(expansionId, handle));
      return true;
    }

    // The reservation has been removed already..so dont add it and just return
    return false;
  }

  synchronized Set<ExpansionId> removeExpansionsOf(UUID reservationId) {
    reservationTotalAsk.remove(reservationId);
    return reservationToExpansionsMap.remove(reservationId);
  }

  synchronized void removeExpansion(UUID reservationId, UUID expansionId) {
    Set<ExpansionId> eIds = reservationToExpansionsMap.get(reservationId);
    if (eIds != null) {
      eIds.remove(new ExpansionId(expansionId, null));
      if (eIds.isEmpty()) {
        reservationToExpansionsMap.remove(reservationId);
      }
    }
  }

  synchronized Set<UUID> getExpansions(UUID reservationId) {
    HashSet<UUID> ret = null;
    Set<ExpansionId> expansions = reservationToExpansionsMap.get
        (reservationId);
    if (expansions != null) {
      ret = new HashSet<UUID>();
      for(ExpansionId expansionId : expansions) {
        ret.add(expansionId.expansionId);
      }
    }
    return ret;
  }

  @Override
  public void expand(UUID expansionId, Expansion expansion)
      throws LlamaException {
    UUID reservationId = expansion.getExpansionOf();
    synchronized (this) {
      if (reservationTotalAsk.get(reservationId) == null) {
        LOG.error("Expansion request for unknown reservation: " +
            "Expansion Id: {}, Reservation Id: {}", expansionId, reservationId);
        throw new LlamaException(ErrorCode.UNKNOWN_RESERVATION_FOR_EXPANSION,
            reservationId);
      }
      checkAndUpdateCapacity(reservationId, expansionId,
          Arrays.asList(expansion.getResource()),_getNodes());
    }

    PlacedReservation originalReservation = am.getReservation(reservationId);
    if (originalReservation == null) {
      throw new LlamaException(ErrorCode.UNKNOWN_RESERVATION_FOR_EXPANSION,
          reservationId);
    }
    if (originalReservation.getExpansionOf() != null) {
      throw new LlamaException(ErrorCode.CANNOT_EXPAND_AN_EXPANSION_RESERVATION,
          reservationId);
    }
    if (originalReservation.getStatus() != PlacedReservation.Status.ALLOCATED) {
      throw new LlamaException(ErrorCode.CANNOT_EXPAND_A_RESERVATION_NOT_ALLOCATED,
          reservationId, originalReservation.getStatus());
    }
    Reservation reservation =
        PlacedReservationImpl.createReservationForExpansion(
            originalReservation, expansion);
    am.reserve(expansionId, reservation);

    // There could be a race condition that by the time we reach here,
    // client could release the reservation, so we should check if its still
    // valid and otherwise release it.
    if (!addExpansion(reservationId, expansionId, expansion.getHandle())) {
      am.releaseReservation(expansion.getHandle(), expansionId, false);
      throw new LlamaException(ErrorCode.UNKNOWN_RESERVATION_FOR_EXPANSION,
          reservationId);
    }
  }

  @Override
  public PlacedReservation getReservation(UUID reservationId)
      throws LlamaException {
    return am.getReservation(reservationId);
  }

  private void releaseExpansions(UUID reservationId, boolean doNotCache) {
    Set<ExpansionId> expansionIds = removeExpansionsOf(reservationId);
    if (expansionIds != null) {
      LOG.debug("Releasing '{}' expansions for reservation '{}'",
          expansionIds.size(), reservationId);
      for (ExpansionId expansionId : expansionIds) {
        try {
          LOG.debug("Releasing expansion '{}' for reservation '{}'",
              expansionId.expansionId, reservationId);
          //events generated by this release should never be echo
          am.releaseReservation(expansionId.handle, expansionId.expansionId, doNotCache);
        } catch (Exception ex) {
          LOG.error("Could not release properly expansion '{}' for " +
              "reservation '{}': {}", expansionId, reservationId, ex.toString(),
              ex);
        }
      }
    }
  }

  private void releaseReservationAndExpansions(PlacedReservation reservation,
      boolean doNotCache) {
    if (reservation.getExpansionOf() == null) {
      releaseExpansions(reservation.getReservationId(), doNotCache);
    } else {
      removeExpansion(reservation.getExpansionOf(), reservation.getReservationId());
    }
  }

  @Override
  public PlacedReservation releaseReservation(UUID handle, UUID reservationId,
      boolean doNotCache) throws LlamaException {
    PlacedReservation reservation = am.getReservation(reservationId);
    if (reservation != null) {
      releaseReservationAndExpansions(reservation, doNotCache);
    }
    LOG.debug("Releasing reservation '{}'", reservationId);
    return am.releaseReservation(handle, reservationId, doNotCache);
  }

  @Override
  public List<PlacedReservation> releaseReservationsForHandle(UUID handle,
      boolean doNotCache) throws LlamaException {
    List<PlacedReservation> reservations =
        am.releaseReservationsForHandle(handle, doNotCache);
    for (PlacedReservation reservation : reservations) {
      releaseReservationAndExpansions(reservation, doNotCache);
    }
    return reservations;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForQueue(String queue,
      boolean doNotCache) throws LlamaException {
    List<PlacedReservation> reservations =
        am.releaseReservationsForQueue(queue, doNotCache);
    for (PlacedReservation reservation : reservations) {
      //all expansions are in the same queue, the underlying AM released them
      //already, we just need to clean up the crossref
      removeExpansionsOf(reservation.getReservationId());
    }
    return reservations;
  }

  @Override
  public void emptyCacheForQueue(String queue) throws LlamaException {
    am.emptyCacheForQueue(queue);
  }

  @Override
  public void onEvent(LlamaAMEvent event) {
    if (!event.isEcho()) {
      for (PlacedReservation reservation : event.getReservationChanges()) {
        if (reservation.getStatus().isFinal()) {
          releaseExpansions(reservation.getReservationId(), false);
        }
      }
    }
    dispatch(LlamaAMEventImpl.convertToImpl(event));
  }
}
