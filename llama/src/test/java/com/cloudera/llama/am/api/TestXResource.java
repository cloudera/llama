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

public class TestXResource {

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail1() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail2() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocationAsk(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail3() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocationAsk("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail4() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocationAsk("l");
    b.build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail5() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocationAsk("l");
    b.setLocalityAsk(Resource.Locality.MUST);
    b.build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail6() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocalityAsk(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail7() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setCpuVCoresAsk(-1);
    b.build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail8() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setMemoryMbsAsk(-1);
    b.build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFail9() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocationAsk("l");
    b.setLocalityAsk(Resource.Locality.MUST);
    b.setCpuVCoresAsk(0);
    b.setMemoryMbsAsk(0);
    b.build();
  }

  @Test
  public void testBuilderOk() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocationAsk("l");
    b.setLocalityAsk(Resource.Locality.MUST);
    b.setCpuVCoresAsk(1);
    b.setMemoryMbsAsk(2);
    Assert.assertNotNull(b.build());
  }

  @Test
  public void testGetters() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocationAsk("l");
    b.setLocalityAsk(Resource.Locality.MUST);
    b.setCpuVCoresAsk(1);
    b.setMemoryMbsAsk(2);
    Resource r = b.build();
    Assert.assertNotNull(r.toString());
    Assert.assertEquals("l", r.getLocationAsk());
    Assert.assertEquals(Resource.Locality.MUST, r.getLocalityAsk());
    Assert.assertEquals(1, r.getCpuVCoresAsk());
    Assert.assertEquals(2, r.getMemoryMbsAsk());
    Assert.assertNotNull(r.toString());
  }

  @Test
  public void testEqualsHashcode() {
    Resource.Builder b = Builders.createResourceBuilder();
    b.setLocationAsk("l");
    b.setLocalityAsk(Resource.Locality.MUST);
    b.setCpuVCoresAsk(1);
    b.setMemoryMbsAsk(2);
    Resource r1 = b.build();
    Assert.assertTrue(r1.equals(r1));
    Assert.assertTrue(r1.hashCode() == r1.hashCode());
    Resource r2 = b.build();
    Assert.assertFalse(r1.equals(r2));
  }

}
