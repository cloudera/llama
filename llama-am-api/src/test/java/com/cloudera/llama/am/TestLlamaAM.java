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
package com.cloudera.llama.am;

import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class TestLlamaAM {
  
  public static class MyLlamaAM extends LlamaAM {
    static boolean created;
    
    public MyLlamaAM() {
      created = true;
    }
    
    @Override
    public Configuration getConf() {
      return null;
    }

    @Override
    public void start() throws LlamaAMException {
    }

    @Override
    public void stop() {
    }

    @Override
    public List<String> getNodes() throws LlamaAMException {
      return null;
    }

    @Override
    public UUID reserve(Reservation reservation) throws LlamaAMException {
      return null;
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
    public void releaseReservationsForClientId(UUID clientId)
        throws LlamaAMException {
    }

    @Override
    public void addListener(LlamaAMListener listener) {
    }

    @Override
    public void removeListener(LlamaAMListener listener) {
    }
  }

  @Test(expected = RuntimeException.class)
  public void testCreateNoClassSet() throws Exception{
    Configuration conf = new Configuration(false);
    LlamaAM.create(conf);
  }

  @Test(expected = RuntimeException.class)
  public void testCreate() throws Exception{
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.CLASS_KEY, LlamaAM.class, MyLlamaAM.class);
    Assert.assertFalse(MyLlamaAM.created);
    LlamaAM.create(conf);
    Assert.assertTrue(MyLlamaAM.created);
  }

}
