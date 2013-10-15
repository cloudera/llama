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

import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.TestReservation;
import com.cloudera.llama.util.UUID;
import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestPlacedReservationImpl {

  @Test
  public void testMethods() {
    List<Resource> resources = new ArrayList<Resource>();
    Resource r = TestReservation.createResource();
    resources.add(r);
    UUID cId = UUID.randomUUID();
    Reservation pr = new Reservation(cId, "q", resources, true);
    PlacedReservationImpl pri = new PlacedReservationImpl(UUID.randomUUID(), pr);
    pr.toString();
    Assert.assertNotNull(pri.getReservationId());
    Assert.assertEquals(PlacedReservation.Status.PENDING, pri.getStatus());
    Assert.assertEquals(1, pri.getResources().size());
    PlacedResource pres = pri.getResources().get(0);
    Assert.assertEquals(r, pres);
    Assert.assertTrue(PlacedResource.class.isInstance(pres));
    Assert.assertEquals("q", pres.getQueue());
    Assert.assertEquals(pri.getClientId(), pres.getClientId());
    Assert.assertEquals(pri.getReservationId(), pres.getReservationId());
    Assert.assertEquals(pri.getResourceImpls(), pri.getResources());
    Assert.assertEquals(r, pri.getResource(r.getClientResourceId()));
    Assert.assertNull(pri.getResource(UUID.randomUUID()));
    pri.setStatus(PlacedReservation.Status.ALLOCATED);
    Assert.assertEquals(PlacedReservation.Status.ALLOCATED, pri.getStatus());
  }

}
