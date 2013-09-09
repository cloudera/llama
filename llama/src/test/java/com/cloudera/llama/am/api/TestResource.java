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

import java.util.UUID;

public class TestResource {

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail1() throws Exception {
    new Resource(null, null, null, 0, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail2() throws Exception {
    new Resource(UUID.randomUUID(), null, null, 0, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail3() throws Exception {
    new Resource(UUID.randomUUID(), "l", null, 0, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail4() throws Exception {
    new Resource(UUID.randomUUID(), "l", Resource.LocationEnforcement.MUST, 0,
        0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail5() throws Exception {
    new Resource(UUID.randomUUID(), "l", Resource.LocationEnforcement.MUST, -1,
        0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorFail6() throws Exception {
    new Resource(UUID.randomUUID(), "l", Resource.LocationEnforcement.MUST, 0,
        -1);
  }

  @Test
  public void testConstructorOk() throws Exception {
    new Resource(UUID.randomUUID(), "l", Resource.LocationEnforcement.MUST, 0,
        1);
  }

  @Test
  public void testConstructor() throws Exception {
    UUID cId = UUID.randomUUID();
    Resource r1 = new Resource(cId, "l", Resource.LocationEnforcement.MUST, 1,
        2);
    Resource r2 = new Resource(r1);
    Assert.assertEquals(r1.getClientResourceId(), r2.getClientResourceId());
    Assert.assertEquals(r1.getLocation(), r2.getLocation());
    Assert.assertEquals(r1.getEnforcement(), r2.getEnforcement());
    Assert.assertEquals(r1.getCpuVCores(), r2.getCpuVCores());
    Assert.assertEquals(r1.getMemoryMb(), r2.getMemoryMb());

  }

  @Test
  public void testGetters() throws Exception {
    UUID cId = UUID.randomUUID();
    Resource r = new Resource(cId, "l", Resource.LocationEnforcement.MUST, 1,
        2);
    r.toString();
    Assert.assertEquals(cId, r.getClientResourceId());
    Assert.assertEquals("l", r.getLocation());
    Assert.assertEquals(Resource.LocationEnforcement.MUST, r.getEnforcement());
    Assert.assertEquals(1, r.getCpuVCores());
    Assert.assertEquals(2, r.getMemoryMb());
  }

  @Test
  public void testHashEquality() throws Exception {
    UUID cId = UUID.randomUUID();
    Resource r1 = new Resource(cId, "l", Resource.LocationEnforcement.MUST, 1,
        2);
    Resource r2 = new Resource(cId, "l", Resource.LocationEnforcement.MUST, 1,
        2);
    Resource r3 = new Resource(UUID.randomUUID(), "l",
        Resource.LocationEnforcement.MUST, 1, 2);
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
