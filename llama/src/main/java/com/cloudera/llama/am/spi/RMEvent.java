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

import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.util.FastFormat;

import com.cloudera.llama.util.UUID;

public class RMEvent {
  private final UUID resourceId;
  private final Object rmResourceId;
  private final PlacedResource.Status status;
  private final int cpuVCores;
  private final int memoryMb;
  private final String location;

  private RMEvent(UUID resourceId, Object rmResourceId, int cpuVCores,
      int memoryMb, String location, PlacedResource.Status status) {
    this.resourceId = resourceId;
    this.rmResourceId = rmResourceId;
    this.status = status;
    this.location = location;
    this.cpuVCores = cpuVCores;
    this.memoryMb = memoryMb;
  }

  public static RMEvent createAllocationEvent(UUID resourceId,
      Object rmResourceId, int vCpuCores, int memoryMb, String location) {
    return new RMEvent(resourceId, rmResourceId, vCpuCores,
        memoryMb, location, PlacedResource.Status.ALLOCATED);
  }

  public static RMEvent createStatusChangeEvent(UUID resourceId,
      PlacedResource.Status status) {
    return new RMEvent(resourceId, null, -1, -1, null, status);
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public Object getRmResourceId() {
    return rmResourceId;
  }

  public PlacedResource.Status getStatus() {
    return status;
  }

  public int getCpuVCores() {
    return cpuVCores;
  }

  public int getMemoryMb() {
    return memoryMb;
  }

  public String getLocation() {
    return location;
  }

  private static final String TO_STRING_ALLOCATED_MSG = "rmEvent[" +
      "resourceId: {} status: {} cpuVCores: {} memoryMb: {} location: {}]";

  private static final String TO_STRING_CHANGED_MSG = "rmEvent[" +
      "resourceId: {} status: {}]";

  public String toString() {
    String msg = (getStatus() == PlacedResource.Status.ALLOCATED)
                 ? TO_STRING_ALLOCATED_MSG : TO_STRING_CHANGED_MSG;
    return FastFormat.format(msg, getResourceId(), getStatus(),
        getCpuVCores(), getMemoryMb(), getLocation());
  }

}
