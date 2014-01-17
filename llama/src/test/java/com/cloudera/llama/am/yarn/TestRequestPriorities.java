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

import org.junit.Assert;
import org.junit.Test;

import com.cloudera.llama.am.api.Resource;

public class TestRequestPriorities {
  @Test
  public void testRequestPriorities() {
    Assert.assertEquals(
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.MUST),
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.MUST));

    Assert.assertTrue(
        YarnRMConnector.getRequestPriority(2048, 1, Resource.Locality.MUST).compareTo(
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.MUST)) > 0);

    Assert.assertTrue(
        YarnRMConnector.getRequestPriority(1024, 2, Resource.Locality.MUST).compareTo(
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.MUST)) > 0);

    Assert.assertTrue(
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.MUST).compareTo(
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.PREFERRED)) > 0);

    Assert.assertTrue(
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.PREFERRED).compareTo(
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.DONT_CARE)) > 0);

    Assert.assertTrue(
        YarnRMConnector.getRequestPriority(1024, 1, Resource.Locality.MUST).compareTo(
        YarnRMConnector.getRequestPriority(2048, 1, Resource.Locality.PREFERRED)) > 0);
  }
}
