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

import com.cloudera.llama.am.LlamaAMEvent;
import com.cloudera.llama.am.LlamaAMException;
import com.cloudera.llama.am.LlamaAMListener;
import com.cloudera.llama.am.PlacedReservation;
import com.cloudera.llama.am.PlacedResource;
import com.cloudera.llama.am.Reservation;
import com.cloudera.llama.am.Resource;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class TestAbstractSingleQueueLlamaAM {

  public static class DummySingleQueueLlamaAM extends 
      AbstractSingleQueueLlamaAM {
    public static DummySingleQueueLlamaAM dummySimpleAM;

    public boolean start = false;
    public boolean stop = false;
    public boolean reserve = false;
    public boolean release = false;

    protected DummySingleQueueLlamaAM() {
      dummySimpleAM = this;
    }

    @Override
    protected void rmRegister(String queue) throws LlamaAMException {
      start = true;
    }

    @Override
    protected void rmUnregister() {
      stop = true;
    }

    @Override
    protected List<String> rmGetNodes() throws LlamaAMException {
      return Arrays.asList("node");
    }

    @Override
    protected void rmReserve(PlacedReservation reservation) 
        throws LlamaAMException {
      reserve = true;
    }

    @Override
    protected void rmRelease(Collection<PlacedResource> resources)
        throws LlamaAMException {
      release = true;
    }

    @Override
    public void rmChanges(List<RMResourceChange> changes) {
      super.rmChanges(changes);
    }
  }

  public static class DummyLlamaAMListener implements LlamaAMListener {
    public LlamaAMEvent event;

    @Override
    public void handle(LlamaAMEvent event) {
      this.event = event;
    }
  }

  private DummySingleQueueLlamaAM createLlamaAM() {
    DummySingleQueueLlamaAM llama = new DummySingleQueueLlamaAM();
    Configuration conf = new Configuration(false);
    conf.set(AbstractSingleQueueLlamaAM.QUEUE_KEY, "queue");
    llama.setConf(conf);
    return llama;
  }

  @Test(expected = IllegalStateException.class)
  public void testMissingQueue() throws Exception {
    DummySingleQueueLlamaAM llama = new DummySingleQueueLlamaAM();
    Configuration conf = new Configuration(false);
    llama.setConf(conf);
    llama.start();
  }

  @Test
  public void testRmStartStop() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
    try {
      Assert.assertFalse(llama.start);
      Assert.assertFalse(llama.stop);
      llama.start();
      Assert.assertTrue(llama.start);
      Assert.assertFalse(llama.stop);
    } finally {
      llama.stop();
      Assert.assertTrue(llama.stop);
      llama.stop();
    }
  }

  @Test
  public void testAddRemoveListener() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
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
  public void testRmReserve() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);

      Assert.assertTrue(llama.reserve);
      Assert.assertFalse(llama.release);

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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);
      llama.releaseReservation(reservationId);
      llama.releaseReservation(UUID.randomUUID());
      Assert.assertTrue(llama.release);
      Assert.assertNull(llama._getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationNoGangOneResource() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);
      RMResourceChange change = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change));
      Assert.assertEquals(1, listener.event.getAllocatedResources().size());
      Assert.assertEquals(1, listener.event.getAllocatedReservationIds().size
          ());
      PlacedResource resource = listener.event.getAllocatedResources().get(0);
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG);
      RMResourceChange change = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change));
      Assert.assertEquals(1, listener.event.getAllocatedResources().size());
      Assert.assertEquals(1, listener.event.getAllocatedReservationIds().size
          ());

      PlacedResource resource = listener.event.getAllocatedResources().get(0);
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (RESOURCE2.getClientResourceId(), "cid2", 4, 5112, "a2");
      llama.rmChanges(Arrays.asList(change1, change2));
      Assert.assertEquals(2, listener.event.getAllocatedResources().size());
      Assert.assertEquals(1, listener.event.getAllocatedReservationIds().size
          ());
      PlacedResource resource1 = listener.event.getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource1.getRmResourceId());
      PlacedResource resource2 = listener.event.getAllocatedResources().get(1);
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (RESOURCE2.getClientResourceId(), "cid2", 4, 5112, "a2");
      llama.rmChanges(Arrays.asList(change1, change2));
      Assert.assertEquals(2, listener.event.getAllocatedResources().size());
      Assert.assertEquals(1, listener.event.getAllocatedReservationIds().size
          ());
      PlacedResource resource1 = listener.event.getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource1.getRmResourceId());
      PlacedResource resource2 = listener.event.getAllocatedResources().get(1);
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      Assert.assertEquals(1, listener.event.getAllocatedResources().size());
      Assert.assertEquals(0, listener.event.getAllocatedReservationIds().size
          ());
      PlacedResource resource1 = listener.event.getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource1.getRmResourceId());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL, 
          reservation.getStatus());
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (RESOURCE2.getClientResourceId(), "cid2", 4, 5112, "a2");
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(1, listener.event.getAllocatedResources().size());
      Assert.assertEquals(1, listener.event.getAllocatedReservationIds().size
          ());
      PlacedResource resource2 = listener.event.getAllocatedResources().get(0);
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      Assert.assertNull(listener.event);
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL, 
          reservation.getStatus());
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (RESOURCE2.getClientResourceId(), "cid2", 4, 5112, "a2");
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertNotNull(listener.event);
      Assert.assertEquals(2, listener.event.getAllocatedResources().size());
      Assert.assertEquals(1, listener.event.getAllocatedReservationIds().size
          ());
      PlacedResource resource1 = listener.event.getAllocatedResources().get(0);
      Assert.assertEquals("cid1", resource1.getRmResourceId());
      PlacedResource resource2 = listener.event.getAllocatedResources().get(1);
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG);
      RMResourceChange change = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.REJECTED);
      llama.rmChanges(Arrays.asList(change));
      Assert.assertEquals(1, listener.event.getRejectedClientResourcesIds()
          .size());
      Assert.assertEquals(1, listener.event.getRejectedReservationIds().size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRejectPartialGangReservation() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE2.getClientResourceId(), PlacedResource.Status.REJECTED);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(1, listener.event.getRejectedClientResourcesIds()
          .size());
      Assert.assertEquals(1, listener.event.getRejectedReservationIds().size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRejectPartialNonGangReservation() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE2.getClientResourceId(), PlacedResource.Status.REJECTED);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(1, listener.event.getRejectedClientResourcesIds()
          .size());
      Assert.assertEquals(0, listener.event.getRejectedReservationIds().size());
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.PREEMPTED);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(1, listener.event.getRejectedReservationIds().size());
      Assert.assertEquals(0, listener.event.getPreemptedClientResourceIds()
          .size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptPartialNonGangReservation() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.PREEMPTED);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(1, listener.event.getPreemptedClientResourceIds()
          .size());
      Assert.assertEquals(0, listener.event.getPreemptedReservationIds().size
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.PREEMPTED);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(0, listener.event.getRejectedReservationIds().size());
      Assert.assertEquals(0, listener.event.getPreemptedReservationIds().size
          ());
      Assert.assertEquals(1, listener.event.getPreemptedClientResourceIds()
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.PREEMPTED);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(0, listener.event.getRejectedReservationIds().size());
      Assert.assertEquals(0, listener.event.getPreemptedReservationIds().size
          ());
      Assert.assertEquals(1, listener.event.getPreemptedClientResourceIds()
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.LOST);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(1, listener.event.getRejectedReservationIds().size());
      Assert.assertEquals(0, listener.event.getPreemptedClientResourceIds()
          .size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testLostPartialNonGangReservation() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.LOST);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(1, listener.event.getLostClientResourcesIds().size());
      Assert.assertEquals(0, listener.event.getRejectedReservationIds().size());
      Assert.assertEquals(0, listener.event.getPreemptedReservationIds().size
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.LOST);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(0, listener.event.getRejectedReservationIds().size());
      Assert.assertEquals(0, listener.event.getPreemptedReservationIds().size
          ());
      Assert.assertEquals(1, listener.event.getLostClientResourcesIds().size());
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG);

      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (RESOURCE1.getClientResourceId(), PlacedResource.Status.LOST);
      llama.rmChanges(Arrays.asList(change2));
      Assert.assertEquals(0, listener.event.getRejectedReservationIds().size());
      Assert.assertEquals(0, listener.event.getPreemptedReservationIds().size
          ());
      Assert.assertEquals(1, listener.event.getLostClientResourcesIds().size());
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
    DummySingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation(
          RESOURCE1.getClientResourceId(), "cid1", 3, 4096, "a1");
      llama.rmChanges(Arrays.asList(change1));
      Assert.assertNull(listener.event);
    } finally {
      llama.stop();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRmChangesNull() throws Exception {
    DummySingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      llama.rmChanges(null);
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testReleaseReservationsForClientId() throws Exception{
    DummySingleQueueLlamaAM llama = createLlamaAM();
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
}
