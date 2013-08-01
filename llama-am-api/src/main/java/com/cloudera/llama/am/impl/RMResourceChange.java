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
  private UUID clientResourceId;
  private String rmResourceId;
  private PlacedResource.Status status;
  private int vCpuCores;
  private int memoryMb;
  private String location;

  RMResourceChange() {
  }

  public static RMResourceChange createResourceAllocation(UUID clientResourceId,
      String rmResourceId, int vCpuCores, int memoryMb, String location) {
    RMResourceChange rc = new RMResourceChange();
    rc.clientResourceId = clientResourceId;
    rc.rmResourceId = rmResourceId;
    rc.status = PlacedResource.Status.ALLOCATED;
    rc.location = location;
    rc.vCpuCores = vCpuCores;
    rc.memoryMb = memoryMb;
    return rc;
  }

  public static RMResourceChange createResourceChange(UUID clientResourceId,
      PlacedResource.Status status) {
    RMResourceChange rc = new RMResourceChange();
    rc.clientResourceId = clientResourceId;
    rc.status = status;
    return rc;
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
