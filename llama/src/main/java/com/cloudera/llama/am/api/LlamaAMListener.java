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

/**
 * A <code>LlamaAMListener</code> can be registered with a {@link LlamaAM} to
 * receive {@link LlamaAMEvent}s.
 *
 * @see LlamaAMEvent
 * @see LlamaAM#addListener(LlamaAMListener)
 * @see LlamaAM#removeListener(LlamaAMListener)
 */
public interface LlamaAMListener {

  /**
   * This method is invoked by the {@link LlamaAM} instance where the listener
   * is registered when an event is available.
   *
   * @param event an event with status changes from the {@link LlamaAM}. The
   * event instance is immutable. Events are never replayed.
   */
  public void onEvent(LlamaAMEvent event);

}
