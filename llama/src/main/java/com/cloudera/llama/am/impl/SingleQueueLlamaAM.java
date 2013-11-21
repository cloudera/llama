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
import com.cloudera.llama.am.api.LlamaAMException;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.RMResource;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.spi.RMEvent;
import com.cloudera.llama.am.spi.RMListener;
import com.cloudera.llama.am.spi.RMConnector;
import com.cloudera.llama.am.yarn.YarnRMConnector;
import com.cloudera.llama.server.MetricUtil;
import com.cloudera.llama.util.FastFormat;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SingleQueueLlamaAM extends LlamaAMImpl implements
    RMListener {

  private static final String METRIC_PREFIX_TEMPLATE = LlamaAM.METRIC_PREFIX +
      "queue({}).";

  private static final String RESERVATIONS_GAUGE_TEMPLATE =
      METRIC_PREFIX_TEMPLATE + "reservations.gauge";
  private static final String RESOURCES_GAUGE_TEMPLATE =
      METRIC_PREFIX_TEMPLATE + "resources.gauge";
  private static final String RESERVATIONS_ALLOCATION_TIMER_TEMPLATE =
      METRIC_PREFIX_TEMPLATE + "reservations-allocation-delay.timer";
  private static final String RESOURCES_ALLOCATION_TIMER_TEMPLATE =
      METRIC_PREFIX_TEMPLATE + "resources-allocation-delay.timer";

  public static final List<String> METRIC_TEMPLATE_KEYS = Arrays.asList(
      RESERVATIONS_GAUGE_TEMPLATE, RESOURCES_GAUGE_TEMPLATE,
      RESERVATIONS_ALLOCATION_TIMER_TEMPLATE,
      RESOURCES_ALLOCATION_TIMER_TEMPLATE);

  public interface Callback {

    public void discardReservation(UUID reservationId);

    public void discardAM(String queue);
  }

  private final String queue;
  private final Map<UUID, PlacedReservationImpl> reservationsMap;
  private final Map<UUID, PlacedResourceImpl> resourcesMap;
  private final Callback callback;
  private String reservationsGaugeKey;
  private String resourcesGaugeKey;
  private String reservationsAllocationTimerKey;
  private String resourcesAllocationTimerKey;
  private RMConnector rmConnector;
  private boolean running;

  public static Class<? extends RMConnector> getRMConnectorClass(
      Configuration conf) {
    return conf.getClass(RM_CONNECTOR_CLASS_KEY, YarnRMConnector.class,
        RMConnector.class);
  }

  public SingleQueueLlamaAM(Configuration conf, String queue,
      Callback callback) {
    super(conf);
    this.queue = queue;
    reservationsMap = new HashMap<UUID, PlacedReservationImpl>();
    resourcesMap = new HashMap<UUID, PlacedResourceImpl>();
    this.callback = callback;
  }

  @Override
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    super.setMetricRegistry(metricRegistry);
    reservationsGaugeKey = FastFormat.format(RESERVATIONS_GAUGE_TEMPLATE, queue);
    resourcesGaugeKey = FastFormat.format(RESOURCES_GAUGE_TEMPLATE,  queue);
    reservationsAllocationTimerKey = FastFormat.format(
        RESERVATIONS_ALLOCATION_TIMER_TEMPLATE, queue);
    resourcesAllocationTimerKey = FastFormat.format(
        RESOURCES_ALLOCATION_TIMER_TEMPLATE, queue);
    if (metricRegistry != null) {
      MetricUtil.registerGauge(metricRegistry, reservationsGaugeKey,
          new Gauge<Integer>() {
            @Override
            public Integer getValue() {
              synchronized (this) {
                return reservationsMap.size();
              }
            }
          });
      MetricUtil.registerGauge(metricRegistry, resourcesGaugeKey,
          new Gauge<Integer>() {
            @Override
            public Integer getValue() {
              synchronized (this) {
                return resourcesMap.size();
              }
            }
          });
      MetricUtil.registerTimer(metricRegistry, reservationsAllocationTimerKey);
      MetricUtil.registerTimer(metricRegistry, resourcesAllocationTimerKey);
    }
  }

  // LlamaAM API

  @Override
  public void start() throws LlamaAMException {
    Class<? extends RMConnector> klass = getRMConnectorClass(getConf());
    rmConnector = ReflectionUtils.newInstance(klass, getConf());
    if (getConf().getBoolean(RESOURCES_CACHING_ENABLED_KEY,
        RESOURCES_CACHING_ENABLED_DEFAULT)) {
      RMConnectorCache connectorCache =
          new RMConnectorCache(getConf(), rmConnector);
      connectorCache.setMetricRegistry(getMetricRegistry());
      rmConnector = connectorCache;
    }
    rmConnector.setLlamaAMCallback(this);
    rmConnector.start();
    if (queue != null) {
      rmConnector.register(queue);
    }
    running = true;
  }

  public RMConnector getRMConnector() {
    return rmConnector;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public synchronized void stop() {
    running = false;
    if (getMetricRegistry() != null) {
      getMetricRegistry().remove(reservationsGaugeKey);
      getMetricRegistry().remove(reservationsGaugeKey);
      getMetricRegistry().remove(reservationsAllocationTimerKey);
      getMetricRegistry().remove(resourcesAllocationTimerKey);
    }
    if (rmConnector != null) {
      if (queue != null) {
        rmConnector.unregister();
      }
      rmConnector.stop();
    }
  }

  @Override
  public List<String> getNodes() throws LlamaAMException {
    return rmConnector.getNodes();
  }

  private void _addReservation(PlacedReservationImpl reservation) {
    UUID reservationId = reservation.getReservationId();
    reservationsMap.put(reservationId, reservation);
    for (PlacedResourceImpl resource : reservation.getPlacedResourceImpls()) {
      resource.setStatus(PlacedResource.Status.PENDING);
      resourcesMap.put(resource.getResourceId(), resource);
    }
  }

  PlacedReservationImpl _getReservation(UUID reservationId) {
    return reservationsMap.get(reservationId);
  }

  private PlacedReservationImpl _deleteReservation(UUID reservationId,
      PlacedReservation.Status status) {
    PlacedReservationImpl reservation = reservationsMap.remove(reservationId);
    if (reservation != null) {
      for (PlacedResource resource : reservation.getPlacedResources()) {
        resourcesMap.remove(resource.getResourceId());
      }
    }
    callback.discardReservation(reservationId);
    if (reservation != null) {
      reservation.setStatus(status);
    }
    return reservation;
  }

  @Override
  @SuppressWarnings("unchecked")
  public PlacedReservation reserve(UUID reservationId,
      final Reservation reservation)
      throws LlamaAMException {
    final PlacedReservationImpl impl = new PlacedReservationImpl(reservationId,
        reservation);
    synchronized (this) {
      _addReservation(new PlacedReservationImpl(impl));
    }
    try {
      rmConnector.reserve((List)impl.getPlacedResourceImpls());
    } catch (LlamaAMException ex) {
      synchronized (this) {
        _deleteReservation(impl.getReservationId(),
            PlacedReservation.Status.REJECTED);
      }
      throw ex;
    }
    return impl;
  }

  @Override
  public PlacedReservation getReservation(final UUID reservationId)
      throws LlamaAMException {
    synchronized (this) {
      return _getReservation(reservationId);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public PlacedReservation releaseReservation(UUID handle,
      final UUID reservationId)
      throws LlamaAMException {
    PlacedReservationImpl reservation;
    synchronized (this) {
      reservation = _getReservation(reservationId);
      if (reservation != null) {
        if (!reservation.getHandle().equals(handle)
            && !handle.equals(ADMIN_HANDLE)) {
          throw new LlamaAMException(FastFormat.format(
              "handle '{}' does not own reservation '{}'", handle,
              reservation.getReservationId()));
        }
        _deleteReservation(reservationId, PlacedReservation.Status.RELEASED);
        LlamaAMEventImpl event = new LlamaAMEventImpl(reservation.getHandle());
        event.getPreemptedReservationIds().add(reservationId);
        dispatch(event);
      }
    }
    if (reservation != null) {
      rmConnector.release((List<RMResource>) (List) reservation
          .getResources());
    } else {
      getLog().warn("Unknown reservationId '{}'", reservationId);
    }
    return reservation;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PlacedReservation> releaseReservationsForHandle(UUID handle)
      throws LlamaAMException {
    List<PlacedReservation> reservations = new ArrayList<PlacedReservation>();
    synchronized (this) {
      for (PlacedReservation reservation :
          new ArrayList<PlacedReservation>(reservationsMap.values())) {
        if (reservation.getHandle().equals(handle)) {
          _deleteReservation(reservation.getReservationId(),
              PlacedReservation.Status.RELEASED);
          reservations.add(reservation);
          getLog().debug(
              "Releasing all reservations for handle '{}', reservationId '{}'",
              handle, reservation.getReservationId());
        }
      }
    }
    List<PlacedReservation> ids =
        new ArrayList<PlacedReservation>(reservations.size());
    for (PlacedReservation reservation : reservations) {
      rmConnector.release((List<RMResource>) (List) reservation
          .getResources());
      ids.add(reservation);
    }
    return ids;
  }

  @Override
  public List<PlacedReservation> releaseReservationsForQueue(String queue)
      throws LlamaAMException {
    List<PlacedReservation> list;
    synchronized (this) {
      list = new ArrayList<PlacedReservation>(reservationsMap.values());
      for (PlacedReservation res : list) {
        releaseReservation(res.getHandle(), res.getReservationId());
        getLog().debug(
            "Releasing all reservations for queue '{}', reservationId '{}'",
            queue, res.getReservationId());
      }
    }
    return list;
  }

  // PRIVATE METHODS

  private List<PlacedResourceImpl> _resourceRejected(
      PlacedResourceImpl resource,
      Map<UUID, LlamaAMEventImpl> eventsMap) {
    List<PlacedResourceImpl> toRelease = null;
    resource.setStatus(PlacedResource.Status.REJECTED);
    UUID reservationId = resource.getReservationId();
    PlacedReservationImpl reservation = reservationsMap.get(reservationId);
    if (reservation == null) {
      getLog().warn("Unknown Reservation '{}' during resource '{}' rejection " +
          "handling", reservationId, resource.getResourceId());
    } else {
      LlamaAMEventImpl event = getEventForClientId(eventsMap,
          reservation.getHandle());
      // if reservation is ALLOCATED, or it is PARTIAL and not GANG we let it be
      // and in the ELSE we notify the resource rejection
      switch (reservation.getStatus()) {
        case PENDING:
        case PARTIAL:
          if (reservation.isGang()) {
            _deleteReservation(reservationId, PlacedReservation.Status.REJECTED);
            toRelease = reservation.getPlacedResourceImpls();
            event.getRejectedReservationIds().add(reservationId);
          }
          event.getRejectedClientResourcesIds().add(resource.getResourceId());
          break;
        case ALLOCATED:
          getLog().warn("Illegal internal state, reservation '{}' is " +
              "ALLOCATED, resource cannot  be rejected '{}'", reservationId,
              resource.getResourceId());
          break;
      }
      event.getChanges().add(new PlacedReservationImpl(reservation));
    }
    return toRelease;
  }

  private void _resourceAllocated(PlacedResourceImpl resource,
      RMEvent change, Map<UUID, LlamaAMEventImpl> eventsMap) {
    resource.setAllocationInfo(change.getLocation(), change.getCpuVCores(),
        change.getMemoryMb());
    UUID reservationId = resource.getReservationId();
    PlacedReservationImpl reservation = reservationsMap.get(reservationId);
    if (reservation == null) {
      getLog().warn("Reservation '{}' during resource allocation handling " +
          "for" + " '{}'", reservationId, resource.getResourceId());
    } else {

      MetricUtil.time(getMetricRegistry(), resourcesAllocationTimerKey,
          System.currentTimeMillis() - reservation.getPlacedOn(),
          new ReservationResourceLogContext(resource));

      LlamaAMEventImpl event = getEventForClientId(eventsMap,
          reservation.getHandle());
      List<PlacedResourceImpl> resources = reservation.getPlacedResourceImpls();
      boolean fulfilled = true;
      for (int i = 0; fulfilled && i < resources.size(); i++) {
        fulfilled = resources.get(i).getStatus() == PlacedResource.Status
            .ALLOCATED;
      }
      if (fulfilled) {
        reservation.setStatus(PlacedReservation.Status.ALLOCATED);
        event.getAllocatedReservationIds().add(reservationId);
        if (reservation.isGang()) {
          event.getAllocatedResources().addAll(
              reservation.getPlacedResourceImpls());
        } else {
          event.getAllocatedResources().add(resource);
        }
        event.getAllocatedGangResources().add(resource);

        MetricUtil.time(getMetricRegistry(), reservationsAllocationTimerKey,
            System.currentTimeMillis() - reservation.getPlacedOn(),
            new ReservationResourceLogContext(reservation));
      } else {
        reservation.setStatus(PlacedReservation.Status.PARTIAL);
        if (!reservation.isGang()) {
          event.getAllocatedResources().add(resource);
        }
      }
      event.getChanges().add(new PlacedReservationImpl(reservation));
    }
  }

  private List<PlacedResourceImpl> _resourcePreempted(
      PlacedResourceImpl resource, Map<UUID, LlamaAMEventImpl> eventsMap) {
    List<PlacedResourceImpl> toRelease = null;
    resource.setStatus(PlacedResource.Status.PREEMPTED);
    UUID reservationId = resource.getReservationId();
    PlacedReservationImpl reservation = reservationsMap.get(reservationId);
    if (reservation == null) {
      getLog().warn("Unknown Reservation '{}' during resource preemption " +
          "handling for" + " '{}'", reservationId, resource.getResourceId());
    } else {
      LlamaAMEventImpl event = getEventForClientId(eventsMap,
          reservation.getHandle());
      switch (reservation.getStatus()) {
        case ALLOCATED:
          event.getPreemptedClientResourceIds().add(resource.getResourceId());
          break;
        case PARTIAL:
          if (reservation.isGang()) {
            _deleteReservation(reservationId,
                PlacedReservation.Status.PREEMPTED);
            toRelease = reservation.getPlacedResourceImpls();
            event.getRejectedReservationIds().add(reservationId);
          } else {
            event.getPreemptedClientResourceIds().add(resource.getResourceId());
          }
          break;
        case PENDING:
          getLog().warn("Illegal internal state, reservation '{}' is PENDING, " +
              "resource '{}' cannot  be preempted, releasing reservation ",
              reservationId, resource.getResourceId());
          _deleteReservation(reservationId, PlacedReservation.Status.PREEMPTED);
          toRelease = reservation.getPlacedResourceImpls();
          event.getRejectedReservationIds().add(reservationId);
          break;
      }
      event.getChanges().add(new PlacedReservationImpl(reservation));
    }
    return toRelease;
  }

  private List<PlacedResourceImpl> _resourceLost(
      PlacedResourceImpl resource, Map<UUID, LlamaAMEventImpl> eventsMap) {
    List<PlacedResourceImpl> toRelease = null;
    resource.setStatus(PlacedResource.Status.LOST);
    UUID reservationId = resource.getReservationId();
    PlacedReservationImpl reservation = reservationsMap.get(reservationId);
    if (reservation == null) {
      getLog().warn("Unknown Reservation '{}' during resource lost handling " +
          "for '{}'", reservationId, resource.getResourceId());
    } else {
      LlamaAMEventImpl event = getEventForClientId(eventsMap,
          reservation.getHandle());
      switch (reservation.getStatus()) {
        case ALLOCATED:
          event.getLostClientResourcesIds().add(resource.getResourceId());
          break;
        case PARTIAL:
          if (reservation.isGang()) {
            _deleteReservation(reservationId, PlacedReservation.Status.LOST);
            toRelease = reservation.getPlacedResourceImpls();
            event.getRejectedReservationIds().add(reservationId);
          } else {
            event.getLostClientResourcesIds().add(resource.getResourceId());
          }
          break;
        case PENDING:
          getLog().warn("RM lost reservation '{}' with resource '{}', " +
              "rejecting reservation", reservationId,
              resource.getResourceId());
          _deleteReservation(reservationId, PlacedReservation.Status.LOST);
          toRelease = reservation.getPlacedResourceImpls();
          event.getRejectedReservationIds().add(reservationId);
          break;
      }
      event.getChanges().add(new PlacedReservationImpl(reservation));
    }
    return toRelease;
  }

  // RMListener API

  @Override
  @SuppressWarnings("unchecked")
  public void onEvent(final List<RMEvent> events) {
    if (events == null) {
      throw new IllegalArgumentException("changes cannot be NULL");
    }
    getLog().trace("onEvent({})", events);
    Map<UUID, LlamaAMEventImpl> eventsMap =
        new HashMap<UUID, LlamaAMEventImpl>();
    List<PlacedResourceImpl> toRelease = new ArrayList<PlacedResourceImpl>();
    synchronized (this) {
      for (RMEvent change : events) {
        PlacedResourceImpl resource = resourcesMap.get(change
            .getResourceId());
        if (resource == null) {
          getLog().warn("Unknown resource '{}'", change.getResourceId());
        } else {
          List<PlacedResourceImpl> release = null;
          switch (change.getStatus()) {
            case REJECTED:
              release = _resourceRejected(resource, eventsMap);
              break;
            case ALLOCATED:
              _resourceAllocated(resource, change, eventsMap);
              break;
            case PREEMPTED:
              release = _resourcePreempted(resource, eventsMap);
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
    if (!toRelease.isEmpty()) {
      try {
        rmConnector.release((List<RMResource>) (List) toRelease);
      } catch (LlamaAMException ex) {
        getLog().warn("release() error: {}", ex.toString(), ex);
      }
    }
    dispatch(eventsMap.values());
  }

  //visible for testing only
  void loseAllReservations() {
    synchronized (this) {
      List<UUID> clientResourceIds =
          new ArrayList<UUID>(resourcesMap.keySet());
      List<RMEvent> changes = new ArrayList<RMEvent>();
      for (UUID clientResourceId : clientResourceIds) {
        changes.add(RMEvent.createStatusChangeEvent(clientResourceId,
            PlacedResource.Status.LOST));
      }
      onEvent(changes);
    }
  }

  @Override
  public void stoppedByRM() {
    getLog().warn("Stopped by '{}'", rmConnector.getClass().getSimpleName());
    loseAllReservations();
    callback.discardAM(queue);
  }

}
