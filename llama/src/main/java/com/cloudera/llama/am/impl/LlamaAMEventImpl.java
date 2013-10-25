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

import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.PlacedResource;

import java.util.ArrayList;
import java.util.List;
import com.cloudera.llama.util.UUID;

public class LlamaAMEventImpl implements LlamaAMEvent {
  private final UUID clientId;
  private final List<UUID> allocatedReservationIds = new ArrayList<UUID>();
  private final List<PlacedResource> allocatedResources =
      new ArrayList<PlacedResource>();
  private final List<UUID> rejectedClientResourceIds = new ArrayList<UUID>();
  private final List<UUID> lostClientResourceIds = new ArrayList<UUID>();
  private final List<UUID> rejectedReservationIds = new ArrayList<UUID>();
  private final List<UUID> preemptedReservationIds = new ArrayList<UUID>();
  private final List<UUID> preemptedResourceIds = new ArrayList<UUID>();
  private final List<PlacedResource> allocatedGangResources =
      new ArrayList<PlacedResource>();
  private final List<PlacedReservation> changes =
      new ArrayList<PlacedReservation>();

  public LlamaAMEventImpl(UUID clientId) {
    this.clientId = clientId;
  }

  @Override
  public boolean isEmpty() {
    return allocatedReservationIds.isEmpty() &&
        allocatedResources.isEmpty() &&
        rejectedClientResourceIds.isEmpty() &&
        lostClientResourceIds.isEmpty() &&
        rejectedReservationIds.isEmpty() &&
        preemptedReservationIds.isEmpty() &&
        preemptedResourceIds.isEmpty();
  }

  @Override
  public UUID getClientId() {
    return clientId;
  }

  @Override
  public List<UUID> getAllocatedReservationIds() {
    return allocatedReservationIds;
  }

  @Override
  public List<PlacedResource> getAllocatedResources() {
    return allocatedResources;
  }

  @Override
  public List<UUID> getRejectedReservationIds() {
    return rejectedReservationIds;
  }

  @Override
  public List<UUID> getRejectedClientResourcesIds() {
    return rejectedClientResourceIds;
  }

  @Override
  public List<UUID> getLostClientResourcesIds() {
    return lostClientResourceIds;
  }

  @Override
  public List<UUID> getPreemptedReservationIds() {
    return preemptedReservationIds;
  }

  @Override
  public List<UUID> getPreemptedClientResourceIds() {
    return preemptedResourceIds;
  }

  public List<PlacedResource> getAllocatedGangResources() {
    return allocatedGangResources;
  }

  @Override
  public List<PlacedReservation> getChanges() {
    return changes;
  }
}
