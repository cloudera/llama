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
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.LlamaAMObserver;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.TestReservation;
import com.cloudera.llama.am.mock.mock.MockLlamaAMFlags;
import com.cloudera.llama.am.mock.mock.MockRMLlamaAMConnector;
import com.cloudera.llama.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestObserverLlamaAM {

  private static Set<String> EXPECTED = new HashSet<String>();

  static {
    EXPECTED.add("start");
    EXPECTED.add("stop");
    EXPECTED.add("isRunning");
    EXPECTED.add("getNodes");
    EXPECTED.add("reserve");
    EXPECTED.add("getReservation");
    EXPECTED.add("releaseReservation");
    EXPECTED.add("releaseReservationsForClientId");
    EXPECTED.add("addListener");
    EXPECTED.add("removeListener");
  }

  public class TestLlamaAM extends LlamaAMImpl {
    private boolean running;
    Set<String> invoked;
    Map<UUID, PlacedReservation> reservations;

    protected TestLlamaAM() {
      super(new Configuration(false));
      invoked = new HashSet<String>();
      reservations = new HashMap<UUID, PlacedReservation>();
    }

    @Override
    public void start() throws LlamaAMException {
      running = true;
      invoked.add("start");
    }

    @Override
    public void stop() {
      running = false;
      invoked.add("stop");
    }

    @Override
    public boolean isRunning() {
      invoked.add("isRunning");
      return running;
    }

    @Override
    public List<String> getNodes() throws LlamaAMException {
      invoked.add("getNodes");
      return null;
    }

    @Override
    public PlacedReservation reserve(UUID reservationId,
        Reservation reservation)
        throws LlamaAMException {
      invoked.add("reserve");
      PlacedReservation pr = new PlacedReservationImpl(reservationId,
          reservation);
      reservations.put(reservationId, pr);
      return pr;
    }

    @Override
    public PlacedReservation getReservation(UUID reservationId)
        throws LlamaAMException {
      invoked.add("getReservation");
      return reservations.get(reservationId);
    }

    @Override
    public PlacedReservation releaseReservation(UUID reservationId)
        throws LlamaAMException {
      invoked.add("releaseReservation");
      return reservations.remove(reservationId);
    }

    @Override
    public List<PlacedReservation> releaseReservationsForHandle(UUID handle)
        throws LlamaAMException {
      invoked.add("releaseReservationsForClientId");
      return new ArrayList<PlacedReservation>(reservations.values());
    }

    @Override
    public void addListener(LlamaAMListener listener) {
      invoked.add("addListener");
      super.addListener(listener);
    }

    @Override
    public void removeListener(LlamaAMListener listener) {
      invoked.add("removeListener");
      super.removeListener(listener);
    }

  }

  public class TestLlamaAMObserver implements LlamaAMObserver {
    private List<PlacedReservation> reservations =
        new ArrayList<PlacedReservation>();
    @Override
    public void observe(PlacedReservation reservation) {
      reservations.add(reservation);
    }
  }

  public static Reservation createReservation() {
    List<Resource> resources = new ArrayList<Resource>();
    resources.add(TestReservation.createResource());
    return new Reservation(UUID.randomUUID(), "q", resources, false);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDelegate() throws Exception {
    TestLlamaAM am = new TestLlamaAM();
    TestLlamaAMObserver observer = new TestLlamaAMObserver();
    ObserverLlamaAM oAm = new ObserverLlamaAM(am, observer);

    Assert.assertFalse(oAm.isRunning());
    oAm.start();
    Assert.assertTrue(oAm.isRunning());
    oAm.getNodes();
    oAm.addListener(null);
    oAm.removeListener(null);
    Reservation<Resource> reservation = createReservation();
    Assert.assertTrue(am.reservations.isEmpty());
    UUID id = oAm.reserve(reservation).getReservationId();
    Assert.assertFalse(am.reservations.isEmpty());
    Assert.assertTrue(am.reservations.containsKey(id));
    oAm.getReservation(id);
    oAm.releaseReservation(id);
    Assert.assertFalse(am.reservations.containsKey(id));
    oAm.releaseReservationsForHandle(UUID.randomUUID());
    oAm.stop();
    Assert.assertFalse(oAm.isRunning());
    Assert.assertEquals(EXPECTED, am.invoked);
  }

  protected Configuration getConfiguration() {
    Configuration conf = new Configuration(false);
    conf.set("llama.am.mock.queues", "q1");
    conf.set("llama.am.mock.events.min.wait.ms", "1");
    conf.set("llama.am.mock.events.max.wait.ms", "5");
    conf.set("llama.am.mock.nodes", "h0");
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "q1");
    conf.set(LlamaAM.RM_CONNECTOR_CLASS_KEY, MockRMLlamaAMConnector.class.getName());
    return conf;
  }

  public static Reservation createReservation(UUID handle, String endStatus, int resourcesCount) {
    List<Resource> resources = new ArrayList<Resource>();
    for (int i = 0; i < resourcesCount; i++) {
      Resource resource = new Resource(UUID.randomUUID(), endStatus + "h0",
          Resource.LocationEnforcement.MUST, i * 10, 2);
      resources.add(resource);
    }
    return new Reservation(handle, "q1", resources, true);
  }

  private void waitFor(boolean predicate, long timeout) throws Exception {
    long start = System.currentTimeMillis();
    while (!predicate && System.currentTimeMillis() - start < timeout) {
      Thread.sleep(10);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserveReserveAllocateRelease() throws Exception {
    TestLlamaAMObserver observer = new TestLlamaAMObserver();
    final LlamaAM llama = LlamaAM.create(getConfiguration(), observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    PlacedReservation pr1 = llama.reserve(createReservation(handle, MockLlamaAMFlags.ALLOCATE, 2));
    waitFor(observer.reservations.size() == 3, 300);
    llama.releaseReservation(pr1.getReservationId());
    waitFor(observer.reservations.size() == 4, 300);
    Assert.assertEquals(4, observer.reservations.size());
    Assert.assertEquals(PlacedReservation.Status.PENDING, observer.reservations.get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.PARTIAL, observer.reservations.get(1).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED, observer.reservations.get(2).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ENDED, observer.reservations.get(3).getStatus());
    llama.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserveReserveReject() throws Exception {
    TestLlamaAMObserver observer = new TestLlamaAMObserver();
    final LlamaAM llama = LlamaAM.create(getConfiguration(), observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    llama.reserve(createReservation(handle, MockLlamaAMFlags.REJECT, 1));
    waitFor(observer.reservations.size() == 2, 300);
    Assert.assertEquals(2, observer.reservations.size());
    Assert.assertEquals(PlacedReservation.Status.PENDING, observer.reservations.get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ENDED, observer.reservations.get(1).getStatus());
    llama.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserveLoseRelease() throws Exception {
    TestLlamaAMObserver observer = new TestLlamaAMObserver();
    final LlamaAM llama = LlamaAM.create(getConfiguration(), observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    PlacedReservation pr1 = llama.reserve(createReservation(handle, MockLlamaAMFlags.LOSE, 1));
    waitFor(observer.reservations.size() == 3, 300);
    llama.releaseReservation(pr1.getReservationId());
    waitFor(observer.reservations.size() == 3, 300);
    Assert.assertEquals(4, observer.reservations.size());
    Assert.assertEquals(PlacedReservation.Status.PENDING, observer.reservations.get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED, observer.reservations.get(1).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED, observer.reservations.get(2).getStatus());
    Assert.assertEquals(PlacedResource.Status.LOST, observer.reservations.get(2).getResources().get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ENDED, observer.reservations.get(3).getStatus());
    llama.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObservePreemptRelease() throws Exception {
    TestLlamaAMObserver observer = new TestLlamaAMObserver();
    final LlamaAM llama = LlamaAM.create(getConfiguration(), observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    PlacedReservation pr1 = llama.reserve(createReservation(handle, MockLlamaAMFlags.PREEMPT, 1));
    waitFor(observer.reservations.size() == 3, 300);
    llama.releaseReservation(pr1.getReservationId());
    waitFor(observer.reservations.size() == 4, 300);
    Assert.assertEquals(4, observer.reservations.size());
    Assert.assertEquals(PlacedReservation.Status.PENDING, observer.reservations.get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED, observer.reservations.get(1).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED, observer.reservations.get(2).getStatus());
    Assert.assertEquals(PlacedResource.Status.PREEMPTED, observer.reservations.get(2).getResources().get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ENDED, observer.reservations.get(3).getStatus());
    llama.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testReleaseReservationsForHandle() throws Exception {
    TestLlamaAMObserver observer = new TestLlamaAMObserver();
    final LlamaAM llama = LlamaAM.create(getConfiguration(), observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    llama.reserve(createReservation(handle, MockLlamaAMFlags.PENDING, 1));
    llama.reserve(createReservation(handle, MockLlamaAMFlags.ALLOCATE, 1));
    waitFor(observer.reservations.size() == 3, 300);
    llama.releaseReservationsForHandle(handle);
    waitFor(observer.reservations.size() == 5, 300);
    Assert.assertEquals(5, observer.reservations.size());
    Assert.assertEquals(PlacedReservation.Status.PENDING, observer.reservations.get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.PENDING, observer.reservations.get(1).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED, observer.reservations.get(2).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ENDED, observer.reservations.get(3).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ENDED, observer.reservations.get(4).getStatus());
    llama.stop();
  }

}
