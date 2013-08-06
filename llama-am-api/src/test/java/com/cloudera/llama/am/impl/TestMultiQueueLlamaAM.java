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

import com.cloudera.llama.am.LlamaAM;
import com.cloudera.llama.am.LlamaAMEvent;
import com.cloudera.llama.am.LlamaAMException;
import com.cloudera.llama.am.LlamaAMListener;
import com.cloudera.llama.am.PlacedReservation;
import com.cloudera.llama.am.Reservation;
import com.cloudera.llama.am.TestReservation;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TestMultiQueueLlamaAM {

  private static Set<String> EXPECTED = new HashSet<String>();
  
  static {
    EXPECTED.add("setConf");
    EXPECTED.add("start");
    EXPECTED.add("stop");
    EXPECTED.add("getNodes");
    EXPECTED.add("reserve");
    EXPECTED.add("releaseReservation");
    EXPECTED.add("addListener");
    EXPECTED.add("releaseReservationForClientId");
    EXPECTED.add("getReservation");
    
  }
  public static class MyLlamaAM extends LlamaAM 
    implements Configurable {
    
    public static Set<String> methods = new HashSet<String>();

    public static LlamaAMListener listener;
    
    private Configuration conf;
    
    public MyLlamaAM() {
      methods.clear();
    }
    
    @Override
    public void setConf(Configuration conf) {
      methods.add("setConf");
      this.conf = conf;
    }

    @Override
    public Configuration getConf() {
      return null;
    }

    @Override
    public void start() throws LlamaAMException {
      methods.add("start");
      if (conf.getBoolean("fail.start", false)) {
        throw new LlamaAMException("");
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getNodes() throws LlamaAMException {
      methods.add("getNodes");
      return Collections.EMPTY_LIST;
    }

    @Override
    public void stop() {
      methods.add("stop");
    }

    @Override
    public void addListener(LlamaAMListener listener) {
      MyLlamaAM.listener = listener;
      methods.add("addListener");
    }

    @Override
    public void removeListener(LlamaAMListener listener) {
    }

    @Override
    public UUID reserve(Reservation reservation) 
      throws LlamaAMException {
      methods.add("reserve");
      return UUID.randomUUID();
    }

    @Override
    public void releaseReservation(UUID reservationId) throws LlamaAMException {
      methods.add("releaseReservation");
    }

    @Override
    public PlacedReservation getReservation(UUID reservationId)
        throws LlamaAMException {
      methods.add("getReservation");
      return null;
    }

    @Override
    public void releaseReservationsForClientId(UUID clientId)
        throws LlamaAMException {
      methods.add("releaseReservationForClientId");
    }
  }
  
  @Test
  public void testMultiQueueDelegation() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.CLASS_KEY, MyLlamaAM.class, LlamaAM.class);
    LlamaAM am = LlamaAM.create(conf);
    try {
      am.start();
      LlamaAMListener listener = new LlamaAMListener() {
        @Override
        public void handle(LlamaAMEvent event) {
        }
      };
      UUID id = am.reserve(new Reservation(UUID.randomUUID(), "q",
          Arrays.asList(TestReservation.createResource()), true));
      am.getNodes();
      am.addListener(listener);
      am.removeListener(listener);
      am.getReservation(id);
      am.releaseReservation(id);
      am.releaseReservationsForClientId(UUID.randomUUID());
      am.stop();

      Assert.assertEquals(EXPECTED, MyLlamaAM.methods);
    } finally {
      am.stop();
    }
  }

  @Test(expected = LlamaAMException.class)
  public void testStartOfDelegatedLlamaAmFail() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.CLASS_KEY, MyLlamaAM.class, LlamaAM.class);
    conf.setBoolean("fail.start", true);
    conf.set(LlamaAM.INITIAL_QUEUES_KEY,"q");
    LlamaAM am = LlamaAM.create(conf);
    am.start();
  }

  @Test(expected = LlamaAMException.class)
  public void testGetAnyLlamaFail() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.CLASS_KEY, MyLlamaAM.class, LlamaAM.class);
    LlamaAM am = LlamaAM.create(conf);
    am.start();
    am.getNodes();
  }

  @Test
  public void testGetReservationUnknown() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.CLASS_KEY, MyLlamaAM.class, LlamaAM.class);
    LlamaAM am = LlamaAM.create(conf);
    am.start();
    Assert.assertNull(am.getReservation(UUID.randomUUID()));
  }

  @Test
  public void testReleaseReservationUnknown() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.CLASS_KEY, MyLlamaAM.class, LlamaAM.class);
    LlamaAM am = LlamaAM.create(conf);
    am.start();
    am.releaseReservation(UUID.randomUUID());
  }

  private boolean listenerCalled;
  
  @Test
  public void testMultiQueueListener() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.CLASS_KEY, MyLlamaAM.class, LlamaAM.class);
    LlamaAM am = LlamaAM.create(conf);
    try {
      am.start();
      LlamaAMListener listener = new LlamaAMListener() {
        @Override
        public void handle(LlamaAMEvent event) {
          listenerCalled = true;
        }
      };
      UUID cId = UUID.randomUUID();
      UUID id = am.reserve(new Reservation(cId, "q",
          Arrays.asList(TestReservation.createResource()), true));
      am.getNodes();
      am.addListener(listener);
      am.getReservation(id);
      am.releaseReservation(id);
      am.releaseReservationsForClientId(UUID.randomUUID());
      Assert.assertFalse(listenerCalled);
      MyLlamaAM.listener.handle(new LlamaAMEventImpl(cId));
      Assert.assertTrue(listenerCalled);
      am.removeListener(listener);
      listenerCalled = false;
      MyLlamaAM.listener.handle(new LlamaAMEventImpl(cId));
      Assert.assertFalse(listenerCalled);
      am.stop();
    } finally {
      am.stop();
    }
  }
}
