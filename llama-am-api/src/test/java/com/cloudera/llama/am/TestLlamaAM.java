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

import com.cloudera.llama.am.spi.RMLlamaAMConnector;
import com.cloudera.llama.am.spi.RMLlamaAMCallback;
import com.cloudera.llama.am.spi.RMPlacedReservation;
import com.cloudera.llama.am.spi.RMPlacedResource;
import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class TestLlamaAM {
  
  public static class MyRMLlamaAMConnector implements RMLlamaAMConnector {
    static boolean created;
    
    public MyRMLlamaAMConnector() {
      created = true;
    }

    @Override
    public void setLlamaAMCallback(RMLlamaAMCallback callback) {
    }

    @Override
    public void register(String queue) throws LlamaAMException {
    }

    @Override
    public void unregister() {
    }

    @Override
    public List<String> getNodes() throws LlamaAMException {
      return null;
    }

    @Override
    public void reserve(RMPlacedReservation reservation)
        throws LlamaAMException {
    }

    @Override
    public void release(Collection<RMPlacedResource> resources)
        throws LlamaAMException {
    }
  }

  private void testCreate(Configuration conf) throws Exception{
    LlamaAM am = LlamaAM.create(conf);
    try {
      am.start();
      am.reserve(new Reservation(UUID.randomUUID(), "q",
          Arrays.asList(new Resource(UUID.randomUUID(), "l",
              Resource.LocationEnforcement.MUST, 1, 1 )), false));
      Assert.assertTrue(MyRMLlamaAMConnector.created);
    } finally {
      am.stop();
    }
  }

  @Test(expected = RuntimeException.class)
  public void testCreateNoClassSet() throws Exception{
    testCreate(new  Configuration(false));
  }

  @Test
  public void testCreate() throws Exception{
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class, 
        RMLlamaAMConnector.class);
    testCreate(conf);
  }

}
