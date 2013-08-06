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

import com.cloudera.llama.am.PlacedReservation;
import com.cloudera.llama.am.PlacedResource;
import com.cloudera.llama.am.Reservation;
import com.cloudera.llama.am.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlacedReservationImpl extends PlacedReservation {
  private final UUID reservationId;
  private Status status;

  @SuppressWarnings("unchecked")
  public PlacedReservationImpl(Reservation reservation) {
    super(reservation);
    reservationId = UUID.randomUUID();
    status = Status.PENDING;
    for (PlacedResource resource : getResources()) {
      ((PlacedResourceImpl) resource).setReservationInfo(
          reservation.getClientId(), reservation.getQueue(), reservationId);
    }
  }

  @Override
  protected List<PlacedResource> copyResources(List<? extends Resource> 
      resources) {
    List<PlacedResource> list = new ArrayList<PlacedResource>();
    for (Resource resource : resources) {
      list.add(new PlacedResourceImpl(resource));
    }
    return list;
  }

  @Override
  public UUID getReservationId() {
    return reservationId;
  }

  public PlacedResource getResource(UUID clientResourceId) {
    List<PlacedResource> list = getResources();
    PlacedResource resource = null;
    for (int i = 0; resource == null && i < list.size(); i++) {
      if (list.get(i).getClientResourceId().equals(clientResourceId)) {
        resource = list.get(i);
      }
    }
    return resource;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  @Override
  public Status getStatus() {
    return status;
  }

  @SuppressWarnings("unchecked")
  List<PlacedResourceImpl> getResourceImpls() {
    return (List<PlacedResourceImpl>) (List) getResources();
  }

}
