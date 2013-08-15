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

import com.cloudera.llama.am.LlamaAMException;

import java.util.Collection;
import java.util.List;

public interface RMLlamaAMConnector {

  public void setLlamaAMCallback(RMLlamaAMCallback callback);

  public void register(String queue) throws LlamaAMException;

  public void unregister();

  public List<String> getNodes() throws LlamaAMException;

  public void reserve(RMPlacedReservation reservation)
      throws LlamaAMException;

  public void release(Collection<RMPlacedResource> resources)
      throws LlamaAMException;

}
