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
import com.cloudera.llama.am.Resource;
import com.cloudera.llama.am.spi.RMLlamaAMConnector;
import com.cloudera.llama.am.spi.RMLlamaAMCallback;
import com.cloudera.llama.am.spi.RMPlacedReservation;
import com.cloudera.llama.am.spi.RMPlacedResource;
import com.cloudera.llama.am.spi.RMResourceChange;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class TestSingleQueueLlamaAM {

  public static class MyRMLlamaAMConnector implements RMLlamaAMConnector {

    public boolean start = false;
    public boolean stop = false;
    public boolean reserve = false;
    public boolean release = false;
    public RMLlamaAMCallback callback;

    protected MyRMLlamaAMConnector() {
    }

    @Override
    public void setLlamaAMCallback(RMLlamaAMCallback callback) {
      this.callback = callback;
    }

    @Override
    public void register(String queue) throws LlamaAMException {
      start = true;
    }

    @Override
    public void unregister() {
      stop = true;
    }

    @Override
    public List<String> getNodes() throws LlamaAMException {
      return Arrays.asList("node");
    }

    @Override
    public void reserve(RMPlacedReservation reservation) 
        throws LlamaAMException {
      reserve = true;
    }

    @Override
    public void release(Collection<RMPlacedResource> resources)
        throws LlamaAMException {
      release = true;
    }

  }

  public static class DummyLlamaAMListener implements LlamaAMListener {
    public List<LlamaAMEvent> events = new ArrayList<LlamaAMEvent>();

    @Override
    public void handle(LlamaAMEvent event) {
      events.add(event);
    }
  }

  private SingleQueueLlamaAM createLlamaAM() {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class, 
        RMLlamaAMConnector.class);
    return new SingleQueueLlamaAM(conf, "queue");
  }

  @Test
  public void testRmStartStop() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      Assert.assertFalse(llama.isRunning());
      llama.start();
      Assert.assertTrue(((MyRMLlamaAMConnector) llama.getRMConnector()).start);
      Assert.assertTrue(llama.isRunning());
      Assert.assertFalse(((MyRMLlamaAMConnector) llama.getRMConnector()).stop);
    } finally {
      llama.stop();
      Assert.assertFalse(llama.isRunning());
      Assert.assertTrue(((MyRMLlamaAMConnector)llama.getRMConnector()).stop);
      llama.stop();
    }
  }

  @Test
  public void testRmStopNoRMConnector() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    llama.stop();
  }

  @Test
  public void testAddRemoveListener() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    LlamaAMListener l = new LlamaAMListener() {
      @Override
      public void handle(LlamaAMEvent event) {
      }
    };
    Assert.assertNull(llama.getListener());
    llama.addListener(l);
    Assert.assertNotNull(llama.getListener());
    llama.removeListener(l);
    Assert.assertNull(llama.getListener());
    llama.removeListener(l);
  }

  private static final Resource RESOURCE1 = new Resource(UUID.randomUUID(), 
      "n1", Resource.LocationEnforcement.DONT_CARE, 1, 1024);

  private static final Resource RESOURCE2 = new Resource(UUID.randomUUID(), 
      "n2", Resource.LocationEnforcement.PREFERRED, 2, 2048);

  private static final Resource RESOURCE3 = new Resource(UUID.randomUUID(),
      "n3", Resource.LocationEnforcement.PREFERRED, 3, 2048);

  private static final List<Resource> RESOURCES1 = Arrays.asList(RESOURCE1);

  private static final List<Resource> RESOURCES2 = Arrays.asList(RESOURCE1, 
      RESOURCE2);

  private static final Reservation RESERVATION1_GANG = new Reservation
      (UUID.randomUUID(), "queue", RESOURCES1, true);

  private static final Reservation RESERVATION2_GANG = new Reservation
      (UUID.randomUUID(), "queue", RESOURCES2, true);

  private static final Reservation RESERVATION1_NONGANG = new Reservation
      (UUID.randomUUID(), "queue", RESOURCES1, false);

  private static final Reservation RESERVATION2_NONGANG = new Reservation
      (UUID.randomUUID(), "queue", RESOURCES2, false);

  @Test
  public void testGetNode() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      llama.reserve(RESERVATION1_NONGANG);
      Assert.assertEquals(Arrays.asList("node"), llama.getNodes());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRmReserve() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);

      Assert.assertTrue(((MyRMLlamaAMConnector)llama.getRMConnector()).reserve);
      Assert.assertFalse(((MyRMLlamaAMConnector)llama.getRMConnector()).release);

      PlacedReservation placedReservation = llama.getReservation(reservationId);
      Assert.assertNotNull(placedReservation);
      Assert.assertEquals(PlacedReservation.Status.PENDING, 
          placedReservation.getStatus());
      Assert.assertEquals(reservationId, placedReservation.getReservationId());
      Assert.assertEquals("queue", placedReservation.getQueue());
      Assert.assertFalse(placedReservation.isGang());
      Assert.assertEquals(1, placedReservation.getResources().size());
      PlacedResource resource = placedReservation.getResources().get(0);
      Assert.assertEquals(RESOURCE1.getClientResourceId(), 
          resource.getClientResourceId());
      Assert.assertEquals(PlacedResource.Status.PENDING, resource.getStatus());
      Assert.assertEquals(-1, resource.getActualCpuVCores());
      Assert.assertEquals(-1, resource.getActualMemoryMb());
      Assert.assertEquals(null, resource.getActualLocation());
      Assert.assertEquals("queue", resource.getQueue());
      Assert.assertEquals(reservationId, resource.getReservationId());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRmRelease() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);
      llama.releaseReservation(reservationId);
      llama.releaseReservation(UUID.randomUUID());
      Assert.assertTrue(((MyRMLlamaAMConnector)llama.getRMConnector()).release);
      Assert.assertNull(llama._getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationNoGangOneResource() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);
      RMResourceChange change = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change));
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedReservationIds().size
          ());
      PlacedResource resource = 
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource.getRmResourceId());
      Assert.assertEquals(3, resource.getActualCpuVCores());
      Assert.assertEquals(4096, resource.getActualMemoryMb());
      Assert.assertEquals("a1", resource.getActualLocation());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationGangOneResource() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG);
      RMResourceChange change = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change));
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedReservationIds().size
          ());

      PlacedResource resource = 
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource.getRmResourceId());
      Assert.assertEquals(3, resource.getActualCpuVCores());
      Assert.assertEquals(4096, resource.getActualMemoryMb());
      Assert.assertEquals("a1", resource.getActualLocation());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationNoGangTwoResources()
      throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (RESOURCE2.getClientResourceId(), "cid2", 4, 5112, "a2");
      llama.changesFromRM(Arrays.asList(change1, change2));
      Assert.assertEquals(2, 
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedReservationIds().size
          ());
      PlacedResource resource1 = 
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource1.getRmResourceId());
      PlacedResource resource2 = 
          listener.events.get(0).getAllocatedResources().get(1);
      Assert.assertEquals("cid2", resource2.getRmResourceId());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationGangTwoResources() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (RESOURCE2.getClientResourceId(), "cid2", 4, 5112, "a2");
      llama.changesFromRM(Arrays.asList(change1, change2));
      Assert.assertEquals(2, 
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedReservationIds().size
          ());
      PlacedResource resource1 = 
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource1.getRmResourceId());
      PlacedResource resource2 = 
          listener.events.get(0).getAllocatedResources().get(1);
      Assert.assertEquals("cid2", resource2.getRmResourceId());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());

    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPartiallyThenFullyAllocateReservationNoGangTwoResources()
      throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getAllocatedReservationIds().size
          ());
      PlacedResource resource1 = 
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource1.getRmResourceId());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL, 
          reservation.getStatus());
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (RESOURCE2.getClientResourceId(), "cid2", 4, 5112, "a2");
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedReservationIds().size
          ());
      PlacedResource resource2 = 
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals("cid2", resource2.getRmResourceId());
      reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());

    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPartiallyThenFullyAllocateReservationGangTwoResources()
      throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      Assert.assertTrue(listener.events.isEmpty());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL, 
          reservation.getStatus());
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (RESOURCE2.getClientResourceId(), "cid2", 4, 5112, "a2");
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertNotNull(listener.events.get(0));
      Assert.assertEquals(2, 
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1, 
          listener.events.get(0).getAllocatedReservationIds().size
          ());
      PlacedResource resource1 = 
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource1.getRmResourceId());
      PlacedResource resource2 = 
          listener.events.get(0).getAllocatedResources().get(1);
      Assert.assertEquals("cid2", resource2.getRmResourceId());
      reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());

    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRejectPendingReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG);
      RMResourceChange change = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.REJECTED);
      llama.changesFromRM(Arrays.asList(change));
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedClientResourcesIds()
          .size());
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRejectPartialGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE2.getClientResourceId(), PlacedResource.Status.REJECTED);
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedClientResourcesIds()
          .size());
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRejectPartialNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE2.getClientResourceId(), PlacedResource.Status.REJECTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedClientResourcesIds()
          .size());
      Assert.assertEquals(0, 
          listener.events.get(0).getRejectedReservationIds().size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptPartialGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.PREEMPTED);
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getPreemptedClientResourceIds()
          .size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptPartialNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.PREEMPTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1,
          listener.events.get(0).getPreemptedClientResourceIds()
          .size());
      Assert.assertEquals(0, 
          listener.events.get(0).getPreemptedReservationIds().size
          ());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptAllocatedGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.PREEMPTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(0, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getPreemptedReservationIds().size
          ());
      Assert.assertEquals(1, 
          listener.events.get(0).getPreemptedClientResourceIds()
          .size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptAllocatedNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.PREEMPTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(0, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getPreemptedReservationIds().size
          ());
      Assert.assertEquals(1, 
          listener.events.get(0).getPreemptedClientResourceIds()
          .size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }


  @Test
  public void testLostPartialGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.LOST);
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getPreemptedClientResourceIds()
          .size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testLostPartialNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.LOST);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1, 
          listener.events.get(0).getLostClientResourcesIds().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getPreemptedReservationIds().size
          ());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testLostAllocatedGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.LOST);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(0, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getPreemptedReservationIds().size
          ());
      Assert.assertEquals(1, 
          listener.events.get(0).getLostClientResourcesIds().size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testLostAllocatedNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.LOST);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(0, 
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0, 
          listener.events.get(0).getPreemptedReservationIds().size
          ());
      Assert.assertEquals(1, 
          listener.events.get(0).getLostClientResourcesIds().size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED, 
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testUnknownResourceRmChange() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation(
          RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      Assert.assertTrue(listener.events.isEmpty());
    } finally {
      llama.stop();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRmChangesNull() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      llama.changesFromRM(null);
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testReleaseReservationsForClientId() throws Exception{
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID cId1 = UUID.randomUUID();
      UUID cId2 = UUID.randomUUID();
      UUID reservationId1 = llama.reserve(new Reservation(cId1, "queue", 
          Arrays.asList(RESOURCE1), true));
      UUID reservationId2 = llama.reserve(new Reservation(cId1, "queue", 
          Arrays.asList(RESOURCE2), true));
      UUID reservationId3 = llama.reserve(new Reservation(cId2, "queue", 
          Arrays.asList(RESOURCE3), true));
      Assert.assertNotNull(llama._getReservation(reservationId1));
      Assert.assertNotNull(llama._getReservation(reservationId2));
      Assert.assertNotNull(llama._getReservation(reservationId3));
      llama.releaseReservationsForClientId(cId1);
      Assert.assertNull(llama._getReservation(reservationId1));
      Assert.assertNull(llama._getReservation(reservationId2));
      Assert.assertNotNull(llama._getReservation(reservationId3));
    } finally {
      llama.stop();
    }
  }


  @Test
  public void testLoseAllReservations() throws Exception{
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID cId1 = UUID.randomUUID();
      UUID cId2 = UUID.randomUUID();
      UUID reservationId1 = llama.reserve(new Reservation(cId1, "queue",
          Arrays.asList(RESOURCE1), true));
      UUID reservationId2 = llama.reserve(new Reservation(cId1, "queue",
          Arrays.asList(RESOURCE2), true));
      UUID reservationId3 = llama.reserve(new Reservation(cId2, "queue",
          Arrays.asList(RESOURCE3), true));
      llama.loseAllReservations();
      Assert.assertNull(llama._getReservation(reservationId1));
      Assert.assertNull(llama._getReservation(reservationId2));
      Assert.assertNull(llama._getReservation(reservationId3));
      Assert.assertEquals(2, listener.events.size());
      Assert.assertEquals(3, 
          listener.events.get(0).getRejectedReservationIds().size() + 
              listener.events.get(1).getRejectedReservationIds().size());
    } finally {
      llama.stop();
    }
  }
}
