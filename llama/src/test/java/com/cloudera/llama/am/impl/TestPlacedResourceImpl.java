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

import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.TestReservation;
import com.cloudera.llama.util.UUID;
import junit.framework.Assert;
import org.junit.Test;

public class TestPlacedResourceImpl {

  @Test
  public void testMethods() {
    PlacedResourceImpl r = new PlacedResourceImpl(TestReservation.
        createResource());
    r.toString();
    Assert.assertEquals(PlacedResource.Status.PENDING, r.getStatus());
    Assert.assertNull(r.getClientId());
    Assert.assertNull(r.getReservationId());
    Assert.assertNull(r.getQueue());
    Assert.assertNull(r.getRmResourceId());
    Assert.assertEquals(-1, r.getActualMemoryMb());
    Assert.assertEquals(-1, r.getActualCpuVCores());
    Assert.assertNull(r.getActualLocation());
    r.toString();
    UUID cId = UUID.randomUUID();
    UUID rId = UUID.randomUUID();
    r.setReservationInfo(cId, "q", rId);
    Assert.assertEquals(cId, r.getClientId());
    Assert.assertEquals(rId, r.getReservationId());
    Assert.assertEquals("q", r.getQueue());
    r.setAllocationInfo(1, 2, "l", "id");
    Assert.assertEquals("id", r.getRmResourceId());
    Assert.assertEquals(1, r.getActualCpuVCores());
    Assert.assertEquals(2, r.getActualMemoryMb());
    Assert.assertEquals("l", r.getActualLocation());
  }

}
