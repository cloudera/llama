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

public class TestMultiResourceAM {

//  public static class DummyLlamaAM extends LlamaAM 
//    implements Configurable {
//    String queue;
//    boolean active;
//    boolean connected;
//
//    @Override
//    public void setConf(Configuration conf) {
//    }
//
//    @Override
//    public Configuration getConf() {
//      return null;
//    }
//
//    @Override
//    protected void start() throws LlamaAMException {
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
//    public void start(String queue) throws LlamaAMException {
//      this.queue = queue;
//      connected = true;
//    }
//
//    @Override
//    public boolean isConnected(String queue) throws LlamaAMException {
//      return connected;
//    }
//
//    @Override
//    @SuppressWarnings("unchecked")
//    public List<String> getQueues() throws LlamaAMException {
//      return (connected) ? Arrays.asList(queue) : Collections.EMPTY_LIST;
//    }
//
//    @Override
//    public void close(String queue) {
//      connected = false;
//    }
//
//    @Override
//    public void stop() {
//      active = false;
//    }
//
//    @Override
//    public boolean isActive() {
//      return active;
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
//    public UUID reserve(Reservation reservation) 
//      throws LlamaAMException {
//      return new RequestIDImpl(new QueueIDImpl("q"));
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
//  }
//  
//    @Test
//  public void testMultiQueueLifeCycle() throws Exception {
//    final Resource resource = new Resource(java.util.UUID.randomUUID(), Arrays.asList("h1"), Resource.LocationEnforcement.DONT_CARE, 1, 1, 1);
//    Configuration conf = new Configuration(false);
//    conf.set(LlamaAM.CLASS_KEY, DummyLlamaAM.class.getName());
//    final LlamaAM thrift = LlamaAM.create(conf);
//    try {
//      Assert.assertTrue(thrift.isActive());
//      Assert.assertTrue(thrift.getQueues().isEmpty());
//      thrift.start("q");
//      Assert.assertEquals(1, thrift.getQueues().size());
//      Assert.assertEquals("q", thrift.getQueues().get(0));
//      Assert.assertTrue(thrift.isConnected("q"));
//      thrift.reserve(new Reservation("q", Arrays.asList(resource), true));
//      thrift.stop("q");
//      Assert.assertTrue(thrift.getQueues().isEmpty());
//      Assert.assertFalse(thrift.isConnected("q"));
//      thrift.start("q");
//      thrift.reserve(new Reservation("q", Arrays.asList(resource), true));
//      AssertUtils.assertException(new Callable<Void>() {
//        @Override
//        public Void call() throws Exception {
//          thrift.reserve(new Reservation("x", Arrays.asList(resource), true));
//          return null;
//        }
//      }, IllegalArgumentException.class);
//      thrift.stop("q");
//      AssertUtils.assertException(new Callable<Void>() {
//        @Override
//        public Void call() throws Exception {
//          thrift.reserve(new Reservation("q", Arrays.asList(resource), true));
//          return null;
//        }
//      }, IllegalArgumentException.class);
//      thrift.start("x");
//    } finally {
//      thrift.stop();
//      Assert.assertFalse(thrift.isActive());
//    }
//  }
//
}
