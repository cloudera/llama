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
package com.cloudera.llama.am.spi;

import com.cloudera.llama.am.api.Resource;
import junit.framework.Assert;
import org.junit.Test;

import java.util.UUID;

public class TestRMPlacedResource {

  @Test
  public void test() {
    Resource resource = new Resource(UUID.randomUUID(), "l",
        Resource.LocationEnforcement.MUST, 1, 2);

    RMPlacedResource pr = new RMPlacedResource(resource) {

      @Override
      public UUID getClientId() {
        return null;
      }

      @Override
      public String getQueue() {
        return null;
      }

      @Override
      public UUID getReservationId() {
        return null;
      }

      @Override
      public String getRmResourceId() {
        return null;
      }

      @Override
      public int getActualCpuVCores() {
        return 0;
      }

      @Override
      public int getActualMemoryMb() {
        return 0;
      }

      @Override
      public String getActualLocation() {
        return null;
      }

      @Override
      public Status getStatus() {
        return null;
      }
    };

    Assert.assertEquals(resource, pr);
    Assert.assertNull(pr.getRmPayload());
    Object o = new Object();
    pr.setRmPayload(o);
    Assert.assertEquals(o, pr.getRmPayload());
  }
}
