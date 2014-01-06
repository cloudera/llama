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

import java.util.List;

/**
 * A <code>RMListener</code> can be registered in an {@link RMConnector} in
 * order to receive events regarding status changes of placed
 * {@link RMResource}s.
 */
public interface RMListener {

  /**
   * If the <code>RMConnector</code> has been stopped by the Resource Manager,
   * the listener is notified via this method.
   */
  public void stoppedByRM();

  /**
   * This method is invoked by the {@link RMConnector} the listener is
   * registered to notify of resource status changes.
   *
   * @param events list of events being notified back to the listener. Events
   * are never replayed.
   */
  public void onEvent(final List<RMEvent> events);

}
