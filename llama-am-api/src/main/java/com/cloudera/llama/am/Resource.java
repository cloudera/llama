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

import com.cloudera.llama.am.impl.FastFormat;
import com.cloudera.llama.am.impl.ParamChecker;

import java.util.UUID;

public class Resource {

  public enum LocationEnforcement {
    MUST, PREFERRED, DONT_CARE
  }
  
  private UUID clientResourceId;
  private String location;
  private LocationEnforcement enforcement;
  private int vCpuCores;
  private int memoryMb;
  
  public Resource(UUID clientResourceId, String location, 
                  LocationEnforcement enforcement,
                  int vCpuCores, int memoryMb) {
    this.clientResourceId = ParamChecker.notNull(clientResourceId, 
      "clientResourceId");
    this.location = ParamChecker.notEmpty(location, "location");
    this.enforcement = ParamChecker.notNull(enforcement, "enforcement");
    this.vCpuCores = ParamChecker.lessThanZero(vCpuCores, "vCpuCores");
    this.memoryMb = ParamChecker.lessThanZero(memoryMb, "memoryMb");
    ParamChecker.asserts((vCpuCores != 0 && memoryMb != 0),
        "vCpuCores and memoryMb cannot be both zero");
  }

  protected Resource(Resource resource) {
    this(resource.getClientResourceId(), resource.getLocation(),
      resource.getEnforcement(), 
      resource.getVCpuCores(), resource.getMemoryMb());
  }
  
  public UUID getClientResourceId() {
    return clientResourceId;
  }
  
  public String getLocation() {
    return location;
  }

  public LocationEnforcement getEnforcement() {
    return enforcement;
  }

  public int getVCpuCores() {
    return vCpuCores;
  }

  public int getMemoryMb() {
    return memoryMb;
  }

  @Override
  public int hashCode() {
    return clientResourceId.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return (obj != null) && (obj instanceof Resource) && 
      clientResourceId.equals(((Resource) obj).clientResourceId);
  }

  private static final String TO_STRING_MSG = "resource[" +
      "client_resource_id: {} vCpuCores: {} memoryMb: {} location: {} " +
      "enforcement: {}]";

  public String toString() {
    return FastFormat.format(TO_STRING_MSG, getClientResourceId(), 
        getVCpuCores(), getMemoryMb(), getLocation(), getEnforcement());
  }

}
