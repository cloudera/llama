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

public class TestRAMUtils {

//  @Test
//  public void cloneConfiguration() {
//    Configuration conf = new Configuration(false);
//    conf.set("a", "A");
//    Configuration clone = LlamaUtils.clone(conf);
//    Assert.assertEquals(1, clone.size());
//    Assert.assertEquals("A", clone.get("a"));
//  }
//  
//  private Resource createResource() {
//    RequestIDImpl requestID = new RequestIDImpl(new QueueIDImpl("q"));
//    PlacedResourceImpl r = new PlacedResourceImpl(Arrays.asList("h"), Resource.LocationEnforcement.DONT_CARE, 1, 1, 1);
//    r.setQueue("q");
//    ResourceID resourceID = new ResourceIDImpl(requestID);
//    r.setResourceID(resourceID);
//    r.setRequestID(requestID);
//    Map<String, String> map = new HashMap<String, String>();
//    map.put("a", "A");
//    r.setAllocationInfo(map);
//    r.setLocation("foo");
//    r.setStatus(Status.ALLOCATED);
//    return r;
//  }
//  
//  
//  @Test
//  public void cloneResource() {
//    Resource r1 = createResource();
//    Resource r2 = LlamaUtils.clone(r1, null);
//    Assert.assertEquals(r1.getLocations(), r2.getLocations());
//    Assert.assertEquals(r1.getEnforcement(), r2.getEnforcement());
//    Assert.assertEquals(r1.getPriority(), r2.getPriority());
//    Assert.assertEquals(r1.getCpuVCores(), r2.getCpuVCores());
//    Assert.assertEquals(r1.getMemoryMb(), r2.getMemoryMb());
//    Assert.assertEquals(r1.getStatus(), r2.getStatus());
//    Assert.assertEquals(r1.getAllocationInfo(), r2.getAllocationInfo());
//    Assert.assertEquals(r1.getResourceID(), r2.getResourceID());
//    Assert.assertEquals(r1.getRequestID(), r2.getRequestID());
//    Assert.assertEquals(r1.getQueue(), r2.getQueue());
//    Assert.assertEquals(r1.getLocation(), r2.getLocation());    
//  }
//
//  @Test
//  public void closureResource() {
//    Resource r1 = createResource();
//    Resource r2 = LlamaUtils.clone(r1, new LlamaUtils.ResourceClosure() {
//      @Override
//      public void modify(PlacedResourceImpl resource) {
//        resource.setStatus(Status.KILLED);
//      }
//    });
//    Assert.assertEquals(r1.getLocations(), r2.getLocations());
//    Assert.assertEquals(r1.getEnforcement(), r2.getEnforcement());
//    Assert.assertEquals(r1.getPriority(), r2.getPriority());
//    Assert.assertEquals(r1.getCpuVCores(), r2.getCpuVCores());
//    Assert.assertEquals(r1.getMemoryMb(), r2.getMemoryMb());
//    Assert.assertEquals(Status.KILLED, r2.getStatus());
//    Assert.assertEquals(r1.getAllocationInfo(), r2.getAllocationInfo());
//    Assert.assertEquals(r1.getResourceID(), r2.getResourceID());
//    Assert.assertEquals(r1.getRequestID(), r2.getRequestID());
//    Assert.assertEquals(r1.getQueue(), r2.getQueue());
//    Assert.assertEquals(r1.getLocation(), r2.getLocation());
//  }
//
//  @Test
//  public void cloneResources() {
//    Resource r1 = createResource();
//    List<Resource> l1 = Arrays.asList(r1);
//    List<Resource> l2 = LlamaUtils.clone(l1, null);
//    Assert.assertEquals(l1.size(), l2.size());
//    Resource r2 = l2.get(0);
//    Assert.assertEquals(r1.getLocations(), r2.getLocations());
//    Assert.assertEquals(r1.getEnforcement(), r2.getEnforcement());
//    Assert.assertEquals(r1.getPriority(), r2.getPriority());
//    Assert.assertEquals(r1.getCpuVCores(), r2.getCpuVCores());
//    Assert.assertEquals(r1.getMemoryMb(), r2.getMemoryMb());
//    Assert.assertEquals(r1.getStatus(), r2.getStatus());
//    Assert.assertEquals(r1.getAllocationInfo(), r2.getAllocationInfo());
//    Assert.assertEquals(r1.getResourceID(), r2.getResourceID());
//    Assert.assertEquals(r1.getRequestID(), r2.getRequestID());
//    Assert.assertEquals(r1.getQueue(), r2.getQueue());
//    Assert.assertEquals(r1.getLocation(), r2.getLocation());
//  }
//
//  @Test
//  public void closureResources() {
//    Resource r1 = createResource();
//    List<Resource> l1 = Arrays.asList(r1);
//    List<Resource> l2 = LlamaUtils.clone(l1, new LlamaUtils.ResourceClosure() {
//      @Override
//      public void modify(PlacedResourceImpl resource) {
//        resource.setStatus(Status.KILLED);
//      }
//    });
//    Assert.assertEquals(l1.size(), l2.size());
//    Resource r2 = l2.get(0);
//    Assert.assertEquals(r1.getLocations(), r2.getLocations());
//    Assert.assertEquals(r1.getEnforcement(), r2.getEnforcement());
//    Assert.assertEquals(r1.getPriority(), r2.getPriority());
//    Assert.assertEquals(r1.getCpuVCores(), r2.getCpuVCores());
//    Assert.assertEquals(r1.getMemoryMb(), r2.getMemoryMb());
//    Assert.assertEquals(Status.KILLED, r2.getStatus());
//    Assert.assertEquals(r1.getAllocationInfo(), r2.getAllocationInfo());
//    Assert.assertEquals(r1.getResourceID(), r2.getResourceID());
//    Assert.assertEquals(r1.getRequestID(), r2.getRequestID());
//    Assert.assertEquals(r1.getQueue(), r2.getQueue());
//    Assert.assertEquals(r1.getLocation(), r2.getLocation());
//  }

}
