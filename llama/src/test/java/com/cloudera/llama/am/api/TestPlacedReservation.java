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
package com.cloudera.llama.am.api;

import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestPlacedReservation {

  public static class MyPlacedReservation extends PlacedReservation {
    private UUID id;

    protected MyPlacedReservation(UUID id, Reservation<Resource> reservation) {
      super(reservation);
      this.id = id;
    }

    @Override
    public UUID getReservationId() {
      return id;
    }

    @Override
    public Status getStatus() {
      return Status.PARTIAL;
    }
  }

  @Test
  public void testToString() {
    List<Resource> resources = new ArrayList<Resource>();
    resources.add(TestReservation.createResource());
    UUID cId = UUID.randomUUID();
    Reservation r = new Reservation(cId, "q", resources, true);

    UUID id1 = UUID.randomUUID();
    PlacedReservation r1 = new MyPlacedReservation(id1, r);
    r1.toString();
  }

  @Test
  public void testHashEquality() {
    List<Resource> resources = new ArrayList<Resource>();
    resources.add(TestReservation.createResource());
    UUID cId = UUID.randomUUID();
    Reservation r = new Reservation(cId, "q", resources, true);

    UUID id1 = UUID.randomUUID();
    PlacedReservation r1 = new MyPlacedReservation(id1, r);
    PlacedReservation r2 = new MyPlacedReservation(id1, r);
    PlacedReservation r3 = new MyPlacedReservation(UUID.randomUUID(), r);

    Assert.assertTrue(r1.equals(r1));
    Assert.assertTrue(r1.equals(r2));
    Assert.assertTrue(r2.equals(r1));
    Assert.assertFalse(r1.equals(r3));
    Assert.assertFalse(r2.equals(r3));
    Assert.assertEquals(r1.hashCode(), r2.hashCode());
    Assert.assertNotSame(r1.hashCode(), r3.hashCode());
    Assert.assertNotSame(r2.hashCode(), r3.hashCode());
  }

}
