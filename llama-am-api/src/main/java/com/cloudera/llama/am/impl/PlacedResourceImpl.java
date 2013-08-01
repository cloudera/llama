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
import com.cloudera.llama.am.Resource;

import java.util.UUID;

public class PlacedResourceImpl extends PlacedResource {
  private UUID clientId;
  private UUID reservationId;
  private String queue;
  private Status status;
  private String rmResourceId;
  private int actualVCpuCores = -1;
  private int actualMemoryMb = -1;
  private String actualLocation;

  public PlacedResourceImpl(Resource resource) {
    super(resource);
    status = Status.PENDING;
  }

  public void setReservationInfo(UUID clientId, String queue,
      UUID reservationId) {
    this.clientId = clientId;
    if (this.reservationId != null) {
      throw new IllegalStateException("reservationId already set");
    }
    this.reservationId = reservationId;
    this.queue = queue;
  }
  
  @Override
  public UUID getClientId() {
    return clientId;
  }

  @Override
  public UUID getReservationId() {
    return reservationId;
  }

  @Override
  public String getRmResourceId() {
    return rmResourceId;
  }

  @Override
  public int getActualVCpuCores() {
    return actualVCpuCores;
  }

  @Override
  public int getActualMemoryMb() {
    return actualMemoryMb;
  }

  @Override
  public String getActualLocation() {
    return actualLocation;
  }

  public void setAllocationInfo(int vCpuCores, int memoryMb, String location,
      String rmResourceId) {
    actualVCpuCores = vCpuCores;
    actualMemoryMb = memoryMb;
    this.actualLocation = location;
    this.rmResourceId = rmResourceId;
    status = Status.ALLOCATED;
  }

  @Override
  public String getQueue() {
    return queue;
  }

  @Override
  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

}