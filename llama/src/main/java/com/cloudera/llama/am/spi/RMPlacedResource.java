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

import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.Resource;

public abstract class RMPlacedResource extends PlacedResource {
  private Object rmPayload;
  
  protected RMPlacedResource(Resource reservation) {
    super(reservation);
  }
  
  public void setRmPayload(Object rmPayload) {
    this.rmPayload = rmPayload;
  }
  
  public Object getRmPayload() {
    return rmPayload;
  }
  
}
