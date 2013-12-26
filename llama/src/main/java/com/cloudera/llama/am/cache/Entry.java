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
package com.cloudera.llama.am.cache;

import com.cloudera.llama.am.api.RMResource;
import com.cloudera.llama.util.FastFormat;
import com.cloudera.llama.util.UUID;

import java.util.Map;

public class Entry implements Comparable<Entry>, CacheRMResource {
  private final UUID cacheId;
  private final long cachedOn;
  private final Object rmResourceId;
  private final String location;
  private final int cpuVCores;
  private final int memoryMb;
  private final Map<String, Object> rmData;
  private volatile boolean valid;

  Entry(String location, int cpuVCores, int memoryMb) {
    cacheId = null;
    cachedOn = 0;
    rmResourceId = null;
    this.location = location;
    this.cpuVCores = cpuVCores;
    this.memoryMb = memoryMb;
    rmData = null;
  }

  Entry(UUID cacheId, RMResource resource, long cachedOn) {
    this.cacheId = cacheId;
    this.cachedOn = cachedOn;
    rmResourceId = resource.getRmResourceId();
    location = resource.getLocation();
    cpuVCores = resource.getCpuVCores();
    memoryMb = resource.getMemoryMbs();
    rmData = resource.getRmData();
  }

  void setValid(boolean valid) {
    this.valid = valid;
  }

  @Override
  public int compareTo(Entry o) {
    return getLocation().compareTo(o.getLocation());
  }

  @Override
  public UUID getResourceId() {
    return cacheId;
  }

  @Override
  public long getCachedOn() {
    return cachedOn;
  }

  @Override
  public Object getRmResourceId() {
    return rmResourceId;
  }

  @Override
  public Locality getLocalityAsk() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getLocationAsk() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCpuVCoresAsk() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getMemoryMbsAsk() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getLocation() {
    return location;
  }

  @Override
  public int getCpuVCores() {
    return cpuVCores;
  }

  @Override
  public int getMemoryMbs() {
    return memoryMb;
  }

  @Override
  public Map<String, Object> getRmData() {
    return rmData;
  }

  @Override
  public void setRmResourceId(Object rmResourceId) {
    throw new UnsupportedOperationException();
  }

  public boolean isValid() {
    return valid;
  }

  private static final String TO_STRING = "ResourceCache [cacheId: {} " +
      "cachedOn: {} rmResourceId: {} location: {} cpuVCores: {} memoryMb: {}]";

  @Override
  public String toString() {
    return FastFormat.format(TO_STRING, getResourceId(), getCachedOn(),
        getRmResourceId(), getLocation(), getCpuVCores(), getMemoryMbs());
  }

}
