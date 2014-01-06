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

import java.util.List;

/**
 * A <code>Reservation</code> defines a request for capacity in the cluster, it
 * comprises of a list of {@link Resource}s defining the total capacity the
 * reservation is asking for; the user that is asking for the reservation;
 * the RM queue from where the reservation must be fulfilled; the handle of the
 * client asking for the reservation and if the reservation is a gang
 * reservation or not.
 * <p/>
 * A gang reservation is a reservation that must be fulfilled at once, without
 * incremental/partial fulfillment.
 * <p/>
 * A <code>Reservation</code> must have at least one {@link Resource}.
 * <p/>
 * Once a <code>Reservation</code> is reserved, it will have a matching
 * {@link PlacedReservation}.
 * <p/>
 * <code>Reservations</code> are created using a {@link Builder}.
 * <p/>
 * A <code>Reservation</code> is immutable.
 *
 * @see Builder
 * @see Builders#createReservationBuilder
 * @see Resource
 * @see PlacedReservation
 * @see LlamaAM#reserve(Reservation)
 */
public interface Reservation {

  /**
   * Builder for {@link Reservation} instances.
   * <p/>
   * Instances are created using the {@link Builders#createReservationBuilder()}
   * method.
   * <p/>
   * A <code>Builder</code> is not thread safe. A <code>Builder</code> can be
   * use to create several {@link Reservation} instances, one at the time, and
   * it can be modified between {@link #build} invocations.
   */
  public interface Builder {

    public Builder setHandle(UUID handle);

    public Builder setUser(String user);

    public Builder setQueue(String queue);

    public Builder addResource(Resource resource);

    public Builder addResources(List<Resource> resources);

    public Builder setResources(List<Resource> resources);

    public Builder setGang(boolean gang);

    public Reservation build();

  }

  public UUID getHandle();

  public String getUser();

  public String getQueue();

  public List<Resource> getResources();

  public boolean isGang();

}
