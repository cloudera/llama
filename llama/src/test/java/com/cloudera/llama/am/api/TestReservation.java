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

public class TestReservation {

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail1() {
    new Reservation(null, null, null, false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail2() {
    new Reservation(UUID.randomUUID(), null, null, false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail3() {
    new Reservation(UUID.randomUUID(), "q", null, false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail4() {
    List<Resource> resources = new ArrayList<Resource>();
    new Reservation(UUID.randomUUID(), "q", resources, false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail5() {
    List<Resource> resources = new ArrayList<Resource>();
    resources.add(null);
    new Reservation(UUID.randomUUID(), "q", resources, false);
  }

  public static Resource createResource() {
    return new Resource(UUID.randomUUID(), "l",
        Resource.LocationEnforcement.MUST, 1, 2);
  }

  @Test
  public void testConstructor() {
    List<Resource> resources = new ArrayList<Resource>();
    resources.add(createResource());
    Reservation r1 = new Reservation(UUID.randomUUID(), "q", resources, false);
    Reservation r2 = new Reservation(r1);
    Assert.assertEquals(r1.getClientId(), r2.getClientId());
    Assert.assertEquals(r1.getQueue(), r2.getQueue());
    Assert.assertEquals(r1.getResources(), r2.getResources());
    Assert.assertEquals(r1.isGang(), r2.isGang());
  }

  @Test
  public void testGetters() {
    List<Resource> resources = new ArrayList<Resource>();
    resources.add(createResource());
    UUID cId = UUID.randomUUID();
    Reservation r = new Reservation(cId, "q", resources, true);
    r.toString();
    Assert.assertEquals(cId, r.getClientId());
    Assert.assertEquals("q", r.getQueue());
    Assert.assertEquals(resources, r.getResources());
    Assert.assertNotSame(resources, r.getResources());
    Assert.assertEquals(true, r.isGang());
  }

}
