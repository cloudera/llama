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
package com.cloudera.llama.am.server.thrift;

import com.cloudera.llama.am.LlamaAMEvent;
import com.cloudera.llama.am.LlamaAMException;
import com.cloudera.llama.am.PlacedResource;
import com.cloudera.llama.am.Reservation;
import com.cloudera.llama.am.Resource;
import com.cloudera.llama.thrift.TAllocatedResource;
import com.cloudera.llama.thrift.TLlamaAMNotificationRequest;
import com.cloudera.llama.thrift.TLlamaAMReservationRequest;
import com.cloudera.llama.thrift.TLlamaServiceVersion;
import com.cloudera.llama.thrift.TResource;
import com.cloudera.llama.thrift.TStatus;
import com.cloudera.llama.thrift.TStatusCode;
import com.cloudera.llama.thrift.TUniqueId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TypeUtils {
  public static final TStatus OK = new TStatus().setStatus_code(TStatusCode.OK);

  public static TStatus createRuntimeError(Throwable ex) {
    TStatus error = new TStatus().setStatus_code(TStatusCode.RUNTIME_ERROR);
    error.setError_msgs(Arrays.asList(ExceptionUtils.getRootCause(ex, 
        LlamaAMException.class).toString()));
    return error;
  }

  public static TStatus createInternalError(Throwable ex) {
    TStatus error = new TStatus().setStatus_code(TStatusCode.INTERNAL_ERROR);
    error.setError_msgs(Arrays.asList(ExceptionUtils.getRootCause(ex, 
        LlamaAMException.class).toString()));
    return error;
  }

  public static UUID toUUID(TUniqueId id) {
    return new UUID(id.getHi(), id.getLo());
  }

  public static TUniqueId toTUniqueId(UUID uuid) {
    return new TUniqueId().setHi(uuid.getMostSignificantBits()).
        setLo(uuid.getLeastSignificantBits());
  }

  public static List<TUniqueId> toTUniqueIds(List<UUID> uuids) {
    List<TUniqueId> ids = new ArrayList<TUniqueId>(uuids.size());
    for (UUID uuid : uuids) {
      ids.add(toTUniqueId(uuid));
    }
    return ids;
  }

  public static Resource toResource(TResource resource, NodeMapper nodeMapper) {
    UUID clientId = toUUID(resource.getClient_resource_id());
    int vCpuCores = resource.getV_cpu_cores();
    int memoryMb = resource.getMemory_mb();
    String location = nodeMapper.getNodeManager(resource.getAskedLocation());
    Resource.LocationEnforcement enforcement = 
        Resource.LocationEnforcement.valueOf(resource.getEnforcement().
            toString());
    return new Resource(clientId, location, enforcement, vCpuCores, memoryMb);
  }
  
  public static List<Resource> toResourceList(List<TResource> tResources,
      NodeMapper nodeMapper) {
    List<Resource> resources = new ArrayList<Resource>(tResources.size());
    for (TResource tResource : tResources) {
      resources.add(toResource(tResource, nodeMapper)); 
    }
    return resources;
  }

  public static Reservation toReservation(TLlamaAMReservationRequest request, 
      NodeMapper nodeMapper) {
    UUID handle = toUUID(request.getAm_handle());
    String queue = request.getQueue();
    boolean isGang = request.isGang();
    List<Resource> resources = toResourceList(request.getResources(), 
        nodeMapper);
    return new Reservation(handle, queue, resources, isGang);  
  }
  
  public static TAllocatedResource toTAllocatedResource(PlacedResource 
      resource, NodeMapper nodeMapper) {
    TAllocatedResource tResource = new TAllocatedResource();
    tResource.setReservation_id(toTUniqueId(resource.getReservationId()));
    tResource.setClient_resource_id(toTUniqueId(resource.getClientResourceId()));
    tResource.setRm_resource_id(resource.getRmResourceId());
    tResource.setV_cpu_cores((short)resource.getActualCpuVCores());
    tResource.setMemory_mb(resource.getActualMemoryMb());
    tResource.setLocation(nodeMapper.getDataNode(resource.getActualLocation()));
    return tResource;
  }
  
  public static List<TAllocatedResource> toTAllocatedResources(
      List<PlacedResource> resources, NodeMapper nodeMapper) {
    List<TAllocatedResource> tResources = 
        new ArrayList<TAllocatedResource>(resources.size());
    for (PlacedResource resource : resources) {
      tResources.add(toTAllocatedResource(resource, nodeMapper));
    }
    return tResources;    
  }

  public static TLlamaAMNotificationRequest createHearbeat(UUID clientId) {
    TLlamaAMNotificationRequest request = new TLlamaAMNotificationRequest();
    request.setVersion(TLlamaServiceVersion.V1);
    request.setAm_handle(toTUniqueId(clientId));
    request.setHeartbeat(true);

    request.setAllocated_reservation_ids(Collections.EMPTY_LIST);
    request.setAllocated_resources(Collections.EMPTY_LIST);
    request.setRejected_reservation_ids(Collections.EMPTY_LIST);
    request.setRejected_client_resource_ids(Collections.EMPTY_LIST);
    request.setLost_client_resource_ids(Collections.EMPTY_LIST);
    request.setPreempted_reservation_ids(Collections.EMPTY_LIST);
    request.setPreempted_client_resource_ids(Collections.EMPTY_LIST);
    return request;
  }

  public static TLlamaAMNotificationRequest toAMNotification(
      LlamaAMEvent event, NodeMapper nodeMapper) {
    TLlamaAMNotificationRequest request = new TLlamaAMNotificationRequest();
    request.setVersion(TLlamaServiceVersion.V1);
    request.setAm_handle(toTUniqueId(event.getClientId()));
    request.setHeartbeat(false);

    request.setAllocated_reservation_ids(toTUniqueIds(
        event.getAllocatedReservationIds()));
    request.setAllocated_resources(toTAllocatedResources(
        event.getAllocatedResources(), nodeMapper));
    request.setRejected_reservation_ids(toTUniqueIds(
        event.getRejectedReservationIds()));
    request.setRejected_client_resource_ids(toTUniqueIds(
        event.getRejectedClientResourcesIds()));
    request.setLost_client_resource_ids(toTUniqueIds(
        event.getLostClientResourcesIds()));
    request.setPreempted_reservation_ids(toTUniqueIds(
        event.getPreemptedReservationIds()));
    request.setPreempted_client_resource_ids(toTUniqueIds(
        event.getPreemptedClientResourceIds()));
    return request; 
  }

  public static boolean isOK(TStatus status) {
    return status.getStatus_code() == TStatusCode.OK;
  }
}
