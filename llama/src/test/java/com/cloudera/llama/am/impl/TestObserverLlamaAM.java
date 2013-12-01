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
import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.TestUtils;
import com.cloudera.llama.am.mock.MockLlamaAMFlags;
import com.cloudera.llama.am.mock.MockRMConnector;
import com.cloudera.llama.util.Clock;
import com.cloudera.llama.util.ManualClock;
import com.cloudera.llama.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
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
    EXPECTED.add("releaseReservationsForQueue");
  }

  private ManualClock clock = new ManualClock();

  @Before
  public void setUp() {
    Clock.setClock(clock);
    clock.set(System.currentTimeMillis());
  }

  @After
  public void cleanUp() {
    Clock.setClock(Clock.SYSTEM);
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
    public void start() throws LlamaException {
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
    public List<String> getNodes() throws LlamaException {
      invoked.add("getNodes");
      return null;
    }

    @Override
    public PlacedReservation reserve(UUID reservationId,
        Reservation reservation)
        throws LlamaException {
      invoked.add("reserve");
      PlacedReservation pr = new PlacedReservationImpl(reservationId,
          reservation);
      reservations.put(reservationId, pr);
      return pr;
    }

    @Override
    public PlacedReservation getReservation(UUID reservationId)
        throws LlamaException {
      invoked.add("getReservation");
      return reservations.get(reservationId);
    }

    @Override
    public PlacedReservation releaseReservation(UUID handle, UUID reservationId)
        throws LlamaException {
      invoked.add("releaseReservation");
      return reservations.remove(reservationId);
    }

    @Override
    public List<PlacedReservation> releaseReservationsForHandle(UUID handle)
        throws LlamaException {
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

    @Override
    @SuppressWarnings("unchecked")
    public List<PlacedReservation> releaseReservationsForQueue(String queue)
        throws LlamaException {
      invoked.add("releaseReservationsForQueue");
      return Collections.EMPTY_LIST;
    }
  }

  public class ObserverListener implements LlamaAMListener {
    private List<LlamaAMEvent> events = new ArrayList<LlamaAMEvent>();

    @Override
    public void onEvent(LlamaAMEvent events) {
      this.events.add(events);
    }

  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDelegate() throws Exception {
    TestLlamaAM am = new TestLlamaAM();
    ObserverListener observer = new ObserverListener();
    ObserverLlamaAM oAm = new ObserverLlamaAM(am);
    oAm.addListener(observer);

    Assert.assertFalse(oAm.isRunning());
    oAm.start();
    Assert.assertTrue(oAm.isRunning());
    oAm.getNodes();
    oAm.addListener(null);
    oAm.removeListener(null);
    Reservation reservation = TestUtils.createReservation(true);
    Assert.assertTrue(am.reservations.isEmpty());
    UUID id = oAm.reserve(reservation).getReservationId();
    Assert.assertFalse(am.reservations.isEmpty());
    Assert.assertTrue(am.reservations.containsKey(id));
    oAm.getReservation(id);
    oAm.releaseReservation(reservation.getHandle(), id);
    Assert.assertFalse(am.reservations.containsKey(id));
    oAm.releaseReservationsForHandle(UUID.randomUUID());
    oAm.releaseReservationsForQueue("q");
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
    conf.set(LlamaAM.RM_CONNECTOR_CLASS_KEY,
        MockRMConnector.class.getName());
    return conf;
  }

  public static Reservation createReservation(UUID handle, String endStatus,
      int resourcesCount) {
    List<Resource> resources = new ArrayList<Resource>();
    for (int i = 0; i < resourcesCount; i++) {
      Resource resource = TestUtils.createResource(endStatus + "h0",
          Resource.Locality.MUST, i * 10, 2);
      resources.add(resource);
    }
    return TestUtils.createReservation(handle, "u", "q1", resources, true);
  }

  public interface Predicate {
    public boolean value();
  }

  private void waitFor(Predicate predicate, long timeout) throws Exception {
    long start = System.currentTimeMillis();
    while (!predicate.value() && System.currentTimeMillis() - start < timeout) {
      Thread.sleep(10);
    }
    Assert.assertTrue(predicate.value());
  }

  private List<PlacedReservation> getAllReservations(List<LlamaAMEvent> events) {
    return TestUtils.getReservations(events, null, true);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserveReserveAllocateRelease() throws Exception {
    final ObserverListener observer = new ObserverListener();
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    llama.addListener(observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    PlacedReservation pr1 = llama.reserve(createReservation(handle,
        MockLlamaAMFlags.ALLOCATE, 2));
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 3;
      }
    }, 100);
    llama.releaseReservation(handle, pr1.getReservationId());
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 4;
      }
    }, 100);
    Assert.assertEquals(PlacedReservation.Status.PENDING,
        getAllReservations(observer.events).get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.PARTIAL,
        getAllReservations(observer.events).get(1).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
        getAllReservations(observer.events).get(2).getStatus());
    Assert.assertEquals(PlacedReservation.Status.RELEASED,
        getAllReservations(observer.events).get(3).getStatus());
    llama.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserveReserveReject() throws Exception {
    final ObserverListener observer = new ObserverListener();
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    llama.addListener(observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    llama.reserve(createReservation(handle, MockLlamaAMFlags.REJECT, 1));
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 2;
      }
    }, 300);
    clock.increment(51);
    Assert.assertEquals(2, getAllReservations(observer.events).size());
    Assert.assertEquals(PlacedReservation.Status.PENDING,
        getAllReservations(observer.events).get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.REJECTED,
        getAllReservations(observer.events).get(1).getStatus());
    llama.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserveLoseRelease() throws Exception {
    final ObserverListener observer = new ObserverListener();
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    llama.addListener(observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    PlacedReservation pr1 = llama.reserve(createReservation(handle, MockLlamaAMFlags.LOSE, 1));
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 2;
      }
    }, 100);
    llama.releaseReservation(handle, pr1.getReservationId());
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 3;
      }
    }, 100);
    Assert.assertEquals(PlacedReservation.Status.PENDING,
        getAllReservations(observer.events).get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
        getAllReservations(observer.events).get(1).getStatus());
    Assert.assertEquals(PlacedReservation.Status.RELEASED,
        getAllReservations(observer.events).get(2).getStatus());
    Assert.assertEquals(PlacedResource.Status.LOST,
        getAllReservations(observer.events).get(2).getPlacedResources().
            get(0).getStatus());
    llama.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObservePreemptRelease() throws Exception {
    final ObserverListener observer = new ObserverListener();
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    llama.addListener(observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    PlacedReservation pr1 = llama.reserve(createReservation(handle, MockLlamaAMFlags.PREEMPT, 1));
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 2;
      }
    }, 100);
    llama.releaseReservation(handle, pr1.getReservationId());
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 3;
      }
    }, 100);
    Assert.assertEquals(PlacedReservation.Status.PENDING,
        getAllReservations(observer.events).get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
        getAllReservations(observer.events).get(1).getStatus());
    Assert.assertEquals(PlacedReservation.Status.RELEASED,
        getAllReservations(observer.events).get(2).getStatus());
    Assert.assertEquals(PlacedResource.Status.PREEMPTED,
        getAllReservations(observer.events).get(2).getPlacedResources().get(0).getStatus());
    llama.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testReleaseReservationsForHandle() throws Exception {
    final ObserverListener observer = new ObserverListener();
    final LlamaAM llama = LlamaAM.create(getConfiguration());
    llama.addListener(observer);

    llama.start();
    UUID handle = UUID.randomUUID();
    llama.reserve(createReservation(handle, MockLlamaAMFlags.PENDING, 1));
    llama.reserve(createReservation(handle, MockLlamaAMFlags.ALLOCATE, 1));
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 3;
      }
    }, 100);
    llama.releaseReservationsForHandle(handle);
    clock.increment(51);
    waitFor(new Predicate() {
      @Override
      public boolean value() {
        return getAllReservations(observer.events).size() == 5;
      }
    }, 100);
    Assert.assertEquals(5, getAllReservations(observer.events).size());
    Assert.assertEquals(PlacedReservation.Status.PENDING, getAllReservations(observer.events).get(0).getStatus());
    Assert.assertEquals(PlacedReservation.Status.PENDING, getAllReservations(observer.events).get(1).getStatus());
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED, getAllReservations(observer.events).get(2).getStatus());
    Assert.assertEquals(PlacedReservation.Status.RELEASED, getAllReservations(observer.events).get(3).getStatus());
    Assert.assertEquals(PlacedReservation.Status.RELEASED, getAllReservations(observer.events).get(4).getStatus());
    llama.stop();
  }

}
