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
 * Events produced by {@link LlamaAM} to notify {@linke PlacedResource} and
 * {@link PlacedReservation} status changes. These events are delivered to
 * {@link LlamaAMListener} registered to the <code>LlamaAM</code>.
 */
public interface LlamaAMEvent {

  /**
   * Indicates if the event is an echo event or not. Echo events are events
   * generated in response to changes of state because of a client operation
   * ({@link LlamaAM#reserve(Reservation)}, {@link LlamaAM#expand(Expansion)},
   * {@link LlamaAM#releaseReservation(UUID, UUID, boolean)}). Changes of events
   * produced because of admin operations of because of changed driven by the
   * Resource Manager are not echo events.
   *
   * @return <code>TRUE</code> if the event is an echo event, <code>FALSE</code>
   * otherwise.
   */
  public boolean isEcho();

  /**
   * Returns the list of {@link PlacedReservation} with status changes.
   * 
   * @return the list of {@link PlacedReservation} with status changes.
   */
  public List<PlacedReservation> getReservationChanges();

  /**
   * Returns the list of {@link PlacedResource} with status changes.
   *
   * @return the list of {@link PlacedResource} with status changes.
   */
  public List<PlacedResource> getResourceChanges();

}
