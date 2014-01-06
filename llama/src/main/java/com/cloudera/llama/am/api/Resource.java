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
package com.cloudera.llama.am.api;

import com.cloudera.llama.util.UUID;

/**
 * A <code>Resource</code> defines a request for a unit of capacity in the
 * cluster, it comprises of an ID, a desired location, the locality constraints,
 * a desired amount of CPU (in terms of virtual cores) and of Memory (in terms
 * of MBs).
 * <p/>
 * A <code>Resource</code> can have zero CPU or Memory, but both cannot be zero.
 * <p/>
 * <code>Resource</code>s are requested as part of {@link Reservation}.
 * <p/>
 * Once a <code>Resource</code> is reserved, it will have a matching
 * {@link PlacedResource}.
 * <p/>
 * <code>Resources</code> are created using a {@link Builder}.
 * <p/>
 * A <code>Resource</code> is immutable.
 *
 * @see Builder
 * @see Builders#createResourceBuilder
 * @see Reservation
 * @see PlacedResource
 */
public interface Resource {

  /**
   * Builder for {@link Resource} instances.
   * <p/>
   * Instances are created using the {@link Builders#createResourceBuilder}
   * method.
   * <p/>
   * A <code>Builder</code> is not thread safe. A <code>Builder</code> can be
   * use to create several {@link Resource} instances, one at the time, and it
   * can be modified between {@link #build} invocations.
   */
  public interface Builder {

    public Builder setResourceId(UUID resourceId);

    public Builder setLocationAsk(String locationAsk);

    public Builder setLocalityAsk(Locality localityAsk);

    public Builder setCpuVCoresAsk(int cpuVCoresAsk);

    public Builder setMemoryMbsAsk(int memoryMbsAsk);

    public Resource build();

  }

  /**
   * Defines the desired locality constraints for a {@link Resource}.
   */
  public enum Locality {

    /**
     * The allocated resource must be in the requested node location.
     * <p/>
     * In Llama <code>1.0.0</code> {@link Locality#PREFERRED} and
     * {@link Locality#DONT_CARE} are handled in the same way.
     */
    MUST,

    /**
     * The allocated resource should be in the requested node location but and
     * alternate location is acceptable.
     */
    PREFERRED,

    /**
     * The allocated resource can be in any node of the cluster.
     */
    DONT_CARE
  }

  public UUID getResourceId();

  public String getLocationAsk();

  public Locality getLocalityAsk();

  public int getCpuVCoresAsk();

  public int getMemoryMbsAsk();

}
