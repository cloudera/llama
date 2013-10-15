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

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.LlamaAMException;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.TestReservation;
import com.cloudera.llama.am.spi.RMLlamaAMCallback;
import com.cloudera.llama.am.spi.RMLlamaAMConnector;
import com.cloudera.llama.am.spi.RMPlacedReservation;
import com.cloudera.llama.am.spi.RMPlacedResource;
import com.cloudera.llama.am.spi.RMResourceChange;
import com.cloudera.llama.util.UUID;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestMultiQueueLlamaAM {

  private static Set<String> EXPECTED = new HashSet<String>();

  static {
    EXPECTED.add("setConf");
    EXPECTED.add("setLlamaAMCallback");
    EXPECTED.add("start");
    EXPECTED.add("stop");
    EXPECTED.add("register");
    EXPECTED.add("unregister");
    EXPECTED.add("getNodes");
    EXPECTED.add("reserve");
    EXPECTED.add("release");
  }

  public static class MyRMLlamaAMConnector implements RMLlamaAMConnector,
      Configurable {
    public static RMLlamaAMCallback callback;
    public static Set<String> methods = new HashSet<String>();

    private Configuration conf;

    public MyRMLlamaAMConnector() {
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
    public void setLlamaAMCallback(RMLlamaAMCallback callback) {
      MyRMLlamaAMConnector.callback = callback;
      methods.add("setLlamaAMCallback");
    }

    @Override
    public void start() throws LlamaAMException {
      methods.add("start");
      if (conf.getBoolean("fail.start", false)) {
        throw new LlamaAMException("");
      }
    }

    @Override
    public void stop() {
      methods.add("stop");
    }

    @Override
    public void register(String queue) throws LlamaAMException {
      methods.add("register");
      if (conf.getBoolean("fail.register", false)) {
        throw new LlamaAMException("");
      }
    }

    @Override
    public void unregister() {
      methods.add("unregister");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getNodes() throws LlamaAMException {
      methods.add("getNodes");
      return Collections.EMPTY_LIST;
    }

    @Override
    public void reserve(RMPlacedReservation reservation)
        throws LlamaAMException {
      methods.add("reserve");
    }

    @Override
    public void release(Collection<RMPlacedResource> resources)
        throws LlamaAMException {
      methods.add("release");
      if (conf.getBoolean("release.fail", false)) {
        throw new LlamaAMException("");
      }
    }

  }

  @Test
  public void testMultiQueueDelegation() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
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
      am.releaseReservationsForHandle(UUID.randomUUID());
      am.stop();

      Assert.assertEquals(EXPECTED, MyRMLlamaAMConnector.methods);
    } finally {
      am.stop();
    }
  }

  @Test(expected = LlamaAMException.class)
  public void testReleaseReservationForClientException() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    conf.setBoolean("release.fail", true);
    LlamaAM am = LlamaAM.create(conf);
    try {
      am.start();
      UUID cId = UUID.randomUUID();
      am.reserve(new Reservation(cId, "q",
          Arrays.asList(TestReservation.createResource()), true));
      am.releaseReservationsForHandle(cId);
    } finally {
      am.stop();
    }
  }

  @Test(expected = LlamaAMException.class)
  public void testReleaseReservationForClientDiffQueuesException()
      throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    conf.setBoolean("release.fail", true);
    LlamaAM am = LlamaAM.create(conf);
    try {
      am.start();
      UUID cId = UUID.randomUUID();
      am.reserve(new Reservation(cId, "q",
          Arrays.asList(TestReservation.createResource()), true));
      am.reserve(new Reservation(cId, "q2",
          Arrays.asList(TestReservation.createResource()), true));
      am.releaseReservationsForHandle(cId);
    } finally {
      am.stop();
    }
  }

  @Test(expected = LlamaAMException.class)
  public void testStartOfDelegatedLlamaAmFail() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    conf.setBoolean("fail.start", true);
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "q");
    LlamaAM am = LlamaAM.create(conf);
    am.start();
  }

  @Test(expected = LlamaAMException.class)
  public void testRegisterOfDelegatedLlamaAmFail() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    conf.setBoolean("fail.register", true);
    conf.set(LlamaAM.INITIAL_QUEUES_KEY, "q");
    LlamaAM am = LlamaAM.create(conf);
    am.start();
  }

  @Test
  public void testGetReservationUnknown() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    LlamaAM am = LlamaAM.create(conf);
    am.start();
    Assert.assertNull(am.getReservation(UUID.randomUUID()));
  }

  @Test
  public void testReleaseReservationUnknown() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    LlamaAM am = LlamaAM.create(conf);
    am.start();
    am.releaseReservation(UUID.randomUUID());
  }

  private boolean listenerCalled;

  @Test
  public void testMultiQueueListener() throws Exception {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
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
      Resource resource = TestReservation.createResource();
      UUID id = am.reserve(new Reservation(cId, "q",
          Arrays.asList(resource), true));
      am.getNodes();
      am.addListener(listener);
      am.getReservation(id);
      Assert.assertFalse(listenerCalled);
      MyRMLlamaAMConnector.callback.changesFromRM(Arrays.asList(RMResourceChange
          .createResourceChange(resource.getClientResourceId(),
              PlacedResource.Status.REJECTED)));
      Assert.assertTrue(listenerCalled);
      am.releaseReservation(id);
      am.releaseReservationsForHandle(UUID.randomUUID());
      am.removeListener(listener);
      listenerCalled = false;
      Assert.assertFalse(listenerCalled);
      am.stop();
    } finally {
      am.stop();
    }
  }
}
