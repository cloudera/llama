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

public class TestAPIContractEnforcerResourceAM {

//  public static class DummyLlamaAM extends LlamaAM {
//    private boolean connected = false;
//    private boolean active = false;
//
//    public DummyLlamaAM() {
//    }
//    
//    @Override
//    public void addListener(LlamaAMListener listener) {
//    }
//
//    @Override
//    public void removeListener(LlamaAMListener listener) {
//    }
//
//    @Override
//    public void close(String queue) {
//      connected = false;
//    }
//
//    @Override
//    public List<String> getQueues() throws LlamaAMException {
//      return null;
//    }
//
//    @Override
//    public boolean isConnected(String queue) throws LlamaAMException {
//      return connected;
//    }
//
//    @Override
//    public void start(String queue) throws LlamaAMException {
//      connected = true;
//    }
//
//    @Override
//    public void start() throws LlamaAMException {
//      active = true;
//    }
//
//    @Override
//    @SuppressWarnings("unchecked")
//    public Set<String> getAvailableNodes() throws LlamaAMException {
//      return Collections.EMPTY_SET;
//    }
//
//    @Override
//    public boolean isActive() {
//      return active;
//    }
//
//    @Override
//    public synchronized void stop() {
//      active = false;
//    }
//
//    @Override
//    public UUID reserve(Reservation reservation) 
//      throws LlamaAMException {
//      return new RequestIDImpl(new QueueIDImpl(reservation.getQueue()));
//    }
//
//    @Override
//    public void releaseReservation(UUID reservationId) throws LlamaAMException {
//    }
//
//    @Override
//    public void releaseReservation(ResourceID id) throws LlamaAMException {
//    }
//
//    @Override
//    public Status getRequestStatus(RequestID id) 
//      throws LlamaAMException {
//      return null;
//    }
//
//    @Override
//    public Status getResourceStatus(ResourceID id) 
//      throws LlamaAMException {
//      return null;
//    }
//
//    @Override
//    public Resource getResource(ResourceID id) throws LlamaAMException {
//      return null;
//    }
//
//    @Override
//    public Reservation getRequest(RequestID id) 
//      throws LlamaAMException {
//      return null;
//    }
//
//    @Override
//    public List<RequestID> getRequests(String queue) 
//      throws LlamaAMException {
//      return null;
//    }
//
//    @Override
//    public List<ResourceID> getResources(String queue) 
//      throws LlamaAMException {
//      return null;
//    }
//
//    @Override
//    public List<RequestID> getRequests(String queue, Status status) 
//      throws LlamaAMException {
//      return null;
//    }
//
//    @Override
//    public List<ResourceID> getResources(String queue, Status status) 
//      throws LlamaAMException {
//      return null;
//    }
//
//  }
//
//  private LlamaAM createRAM() throws Exception {
//    DummyLlamaAM thrift = new DummyLlamaAM();
//    thrift.start();
//    return new APIContractEnforcerLlamaAM(thrift);
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testRequestNullQueue() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.reserve(new Reservation(null, null, false));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testRequestEmptyQueue() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.reserve(new Reservation(" ", null, false));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testRequestOtherQueue() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.reserve(new Reservation("o", null, false));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testRequestNullResources() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.reserve(new Reservation("q", null, false));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testRequestEmptyResources() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.reserve(new Reservation("q", new ArrayList<Resource>(), false));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testRequestNullResourceElements() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      List<Resource> resources = new ArrayList<Resource>();
//      resources.add(null);
//      thrift.reserve(new Reservation("q", resources, false));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test
//  public void testRequestOK() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.start("q");
//      RequestID id = thrift.reserve(new Reservation("q", Arrays.asList(
//        new Resource(java.util.UUID.randomUUID(), Arrays.asList("h1"), Resource.LocationEnforcement.DONT_CARE, 1, 1, 1)), false));
//      Assert.assertNotNull(id);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testCancelRequestNull() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.releaseReservation((RequestID) null);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test
//  public void testCancelRequestOK() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.releaseReservation(new RequestIDImpl(new QueueIDImpl("q")));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testCancelResourceNull() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.releaseReservation((ResourceID) null);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test
//  public void testCancelResourceOK() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.releaseReservation(new ResourceIDImpl(new RequestIDImpl(new QueueIDImpl("q"))));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetResourceNull() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getResource(null);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test
//  public void testGetResourceUnknownResource() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getResource(new ResourceIDImpl(new RequestIDImpl(
//        new QueueIDImpl("q"))));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetResourcesNull() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getRequest((RequestID) null);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test
//  public void testGetResourcesUnknownRequest() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getRequest(new RequestIDImpl(new QueueIDImpl("q")));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetResourcesStatusInvalidQueue() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getResources("o", null);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetResourcesQueueStatusNull() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getResources("q", null);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetResourcesStatusRejected() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getResources("q", Status.REJECTED);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetResourcesStatusUnknown() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getResources("q", Status.UNKNOWN);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetResourcesStatusKilled() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getResources("q", Status.KILLED);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test
//  public void testGetResourcesALLStatusOK() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getResources(LlamaAM.ALL_QUEUES, Status.ALLOCATED);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetRequestsStatusNull() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getRequestStatus(null);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetRequestsQueueStatusUnknown() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getRequests("q", Status.UNKNOWN);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test(expected = IllegalArgumentException.class)
//  public void testGetRequestsQueueStatusNull() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.getRequests("q", null);
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test
//  public void testReturns() throws Exception {
//    LlamaAM thrift = createRAM();
//    try {
//      thrift.start("q");
//      RequestIDImpl req = new RequestIDImpl(new QueueIDImpl("q"));
//      Assert.assertNull(thrift.getResource(new ResourceIDImpl(req)));
//      Assert.assertNull(thrift.getRequest(req));
//      Assert.assertNotNull(thrift.getRequests("q"));
//      Assert.assertNotNull(thrift.getResources("q", Status.ALLOCATED));
//      Assert.assertEquals(Status.UNKNOWN, 
//        thrift.getRequestStatus(req));
//    } finally {
//      thrift.stop();
//    }
//  }
//
//  @Test
//  public void testMethodsPostClose() throws Exception {
//    final RequestIDImpl req = new RequestIDImpl(new QueueIDImpl("q"));
//    final LlamaAM thrift = createRAM();
//    Assert.assertTrue(thrift.isActive());
//    thrift.stop();
//    Assert.assertFalse(thrift.isActive());
//    AssertUtils.assertException(new Callable<Object>() {
//      @Override
//      public Object call() throws Exception {
//        thrift.removeListener(null);
//        return null;
//      }
//    }, IllegalStateException.class);
//    AssertUtils.assertException(new Callable<Object>() {
//      @Override
//      public Object call() throws Exception {
//        thrift.addListener(null);
//        return null;
//      }
//    }, IllegalStateException.class);
//    AssertUtils.assertException(new Callable<Object>() {
//      @Override
//      public Object call() throws Exception {
//        thrift.getRequests(LlamaAM.ALL_QUEUES);
//        return null;
//      }
//    }, IllegalStateException.class);
//    AssertUtils.assertException(new Callable<Object>() {
//      @Override
//      public Object call() throws Exception {
//        thrift.getRequests(LlamaAM.ALL_QUEUES, Status.PENDING);
//        return null;
//      }
//    }, IllegalStateException.class);
//    AssertUtils.assertException(new Callable<Object>() {
//      @Override
//      public Object call() throws Exception {
//        thrift.getResource(new ResourceIDImpl(req));
//        return null;
//      }
//    }, IllegalStateException.class);
//    AssertUtils.assertException(new Callable<Object>() {
//      @Override
//      public Object call() throws Exception {
//        thrift.getResources("q", Status.ALLOCATED);
//        return null;
//      }
//    }, IllegalStateException.class);
//    AssertUtils.assertException(new Callable<Object>() {
//      @Override
//      public Object call() throws Exception {
//        thrift.getRequestStatus(req);
//        return null;
//      }
//    }, IllegalStateException.class);
//    AssertUtils.assertException(new Callable<Object>() {
//      @Override
//      public Object call() throws Exception {
//        thrift.getResourceStatus(new ResourceIDImpl(req));
//        return null;
//      }
//    }, IllegalStateException.class);
//
//  }

}
