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
 * An <code>Expansion</code> is a special type of {@link Reservation} which
 * purpose is to expand the capacity of {@link Reservation} with a single
 * additional {@link Resource}.
 * <p/>
 * Once an <code>Expansion</code> is reserved, it will have a matching
 * {@link PlacedReservation}.
 * <p/>
 * <code>Expansions</code> are created using a {@link Builder}.
 * <p/>
 * An <code>Expansion</code> is immutable.
 *
 * @see Builder
 * @see Builders#createExpansionBuilder
 * @see Resource
 * @see PlacedReservation
 * @see LlamaAM#expand(Expansion)
 */
public interface Expansion {

  /**
   * Builder for {@link Expansion} instances.
   * <p/>
   * Instances are created using the {@link Builders#createExpansionBuilder()}
   * method.
   * <p/>
   * A <code>Builder</code> is not thread safe. A <code>Builder</code> can be
   * use to create several {@link Expansion} instances, one at the time, and it
   * can be modified between {@link #build} invocations.
   */
  public interface Builder {

    public Builder setHandle(UUID handle);

    public Builder setExpansionOf(UUID expansionOf);

    public Builder setResource(Resource resource);

    public Expansion build();

  }

  public UUID getHandle();

  public UUID getExpansionOf();

  public Resource getResource();

}
