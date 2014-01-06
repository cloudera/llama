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
import com.cloudera.llama.util.UUID;

import java.util.Map;

/**
 * A <code>RMResource</code> represents a single resource requested to the
 * Resource Manager via a {@link RMConnector} instance.
 * <p/>
 * The {@link #getResourceId()} is the client ID of the resource, the
 * {@link #getRmResourceId()} is the Resource Manager ID of the resource.
 * <p/>
 * The {@link #getRmData()} Map can be used by the {@link RMConnector}
 * implementations to store RM specific data associated with the resource for
 * use by the connector itself while the resource is active.
 */
public interface RMResource extends Resource {

  public String getLocation();

  public int getCpuVCores();

  public int getMemoryMbs();

  public UUID getResourceId();

  public Object getRmResourceId();

  public void setRmResourceId(Object rmResourceId);

  public Map<String, Object> getRmData();

}
