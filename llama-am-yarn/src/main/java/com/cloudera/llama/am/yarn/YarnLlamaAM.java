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
package com.cloudera.llama.am.yarn;

import com.cloudera.llama.am.LlamaAMException;
import com.cloudera.llama.am.PlacedReservation;
import com.cloudera.llama.am.PlacedResource;
import com.cloudera.llama.am.impl.AbstractSingleQueueLlamaAM;

import java.util.Collection;
import java.util.List;

public class YarnLlamaAM extends AbstractSingleQueueLlamaAM {
  @Override
  protected void rmStart(String queue) throws LlamaAMException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void rmStop() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected List<String> rmGetNodes() throws LlamaAMException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void rmReserve(PlacedReservation reservation)
      throws LlamaAMException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void rmRelease(Collection<PlacedResource> resources)
      throws LlamaAMException {
    throw new UnsupportedOperationException();
  }

}
