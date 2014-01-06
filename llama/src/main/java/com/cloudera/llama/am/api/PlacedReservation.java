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
 * A <code>PlacedReservation</code> represents a {@link Reservation} or a
 * {@link Expansion} that has been reserved.
 * <p/>
 * In defines its current status and also if the reservation is active or
 * queued.
 * <p/>
 * A returned <code>PlacedReservation</code> instance via the {@link LlamaAM}
 * API is immutable.
 * <p/>
 *
 * @see PlacedResource
 * @see Reservation
 * @see Expansion
 * @see LlamaAM
 */
public interface PlacedReservation extends Reservation {

  /**
   * Possible status of a {@link PlacedReservation}.
   * <p/>
   * The <code>final</code> property indicates if the status is final or not.
   * A final status means that the reservation status cannot change anymore.
   */
  public enum Status {
    PENDING(false),
    BACKED_OFF(false),
    PARTIAL(false),
    ALLOCATED(false),
    REJECTED(true),
    PREEMPTED(true),
    LOST(true),
    RELEASED(true);

    private boolean finalStatus;

    private Status(boolean finalStatus) {
      this.finalStatus = finalStatus;
    }

    public boolean isFinal() {
      return finalStatus;
    }
    }

  public long getPlacedOn();

  public UUID getReservationId();

  public UUID getExpansionOf();

  public Status getStatus();

  public List<PlacedResource> getPlacedResources();

  public long getAllocatedOn();

  public boolean isQueued();

}
