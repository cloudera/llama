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

import com.cloudera.llama.am.LlamaAMEvent;
import com.cloudera.llama.am.LlamaAMException;
import com.cloudera.llama.am.LlamaAMListener;
import com.cloudera.llama.am.PlacedReservation;
import com.cloudera.llama.am.Reservation;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class TestLlamaAMImpl {

  public class MyLlamaAMImpl extends LlamaAMImpl {
    protected MyLlamaAMImpl(Configuration conf) {
      super(conf);
    }

    @Override
    public void start() throws LlamaAMException {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public List<String> getNodes() throws LlamaAMException {
      return null;
    }

    @Override
    public void reserve(UUID reservationId, Reservation reservation)
        throws LlamaAMException {
    }

    @Override
    public PlacedReservation getReservation(UUID reservationId)
        throws LlamaAMException {
      return null;
    }

    @Override
    public void releaseReservation(UUID reservationId) throws LlamaAMException {
    }

    @Override
    public List<UUID> releaseReservationsForClientId(UUID clientId)
        throws LlamaAMException {
      return null;
    }
  }

  public class MyListener implements LlamaAMListener {
    LlamaAMEvent event;
    boolean throwEx;
    @Override
    public void handle(LlamaAMEvent event) {
      this.event = event;
      if (throwEx) {
        throw new RuntimeException();
      }
    }
  }

  @Test
  public void testListeners() {
    MyListener listener = new MyListener();
    LlamaAMImpl am = new MyLlamaAMImpl(new Configuration(false));
    am.addListener(listener);
    LlamaAMEventImpl event = new LlamaAMEventImpl(UUID.randomUUID());
    am.dispatch(event);
    Assert.assertEquals(event, listener.event);
    listener.event = null;
    am.dispatch(Arrays.asList(event));
    Assert.assertNull(listener.event);
    event.getAllocatedReservationIds().add(UUID.randomUUID());
    am.dispatch(Arrays.asList(event));
    Assert.assertEquals(event, listener.event);
    listener.event = null;
    listener.throwEx = true;
    am.dispatch(event);
    Assert.assertEquals(event, listener.event);
    listener.event = null;
    am.removeListener(listener);
    am.dispatch(event);
    Assert.assertNull(listener.event);
    am.dispatch(Arrays.asList(event));
    Assert.assertNull(listener.event);
  }

}
