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

import com.cloudera.llama.am.PlacedResource;

import java.util.UUID;

public class RMResourceChange {
  private final UUID clientResourceId;
  private final String rmResourceId;
  private final PlacedResource.Status status;
  private final int vCpuCores;
  private final int memoryMb;
  private final String location;

  private RMResourceChange(UUID clientResourceId,
      String rmResourceId, int vCpuCores, int memoryMb, String location, 
      PlacedResource.Status status) {
    this.clientResourceId = clientResourceId;
    this.rmResourceId = rmResourceId;
    this.status = status;
    this.location = location;
    this.vCpuCores = vCpuCores;
    this.memoryMb = memoryMb;
  }

  public static RMResourceChange createResourceAllocation(UUID clientResourceId,
      String rmResourceId, int vCpuCores, int memoryMb, String location) {
    return new RMResourceChange(clientResourceId, rmResourceId, vCpuCores,
        memoryMb, location, PlacedResource.Status.ALLOCATED);
  }

  public static RMResourceChange createResourceChange(UUID clientResourceId,
      PlacedResource.Status status) {
    return new RMResourceChange(clientResourceId, null, -1, -1, null, status);
  }

  public UUID getClientResourceId() {
    return clientResourceId;
  }

  public String getRmResourceId() {
    return rmResourceId;
  }

  public PlacedResource.Status getStatus() {
    return status;
  }

  public int getvCpuCores() {
    return vCpuCores;
  }

  public int getMemoryMb() {
    return memoryMb;
  }

  public String getLocation() {
    return location;
  }

  private static final String TO_STRING_ALLOCATED_MSG = "resourceChange" +
      "[clientResourceId: {} status: {} vCpuCores: {} memoryMb: {} " +
      "location: {}]";

  private static final String TO_STRING_CHANGED_MSG = "resourceChange" +
      "[clientResourceId: {} status: {}]";

  public String toString() {
    String msg = (getStatus() == PlacedResource.Status.ALLOCATED)
                 ? TO_STRING_ALLOCATED_MSG : TO_STRING_CHANGED_MSG;
    return FastFormat.format(msg, getClientResourceId(), getStatus(),
        getvCpuCores(), getMemoryMb(), getLocation());
  }

}
