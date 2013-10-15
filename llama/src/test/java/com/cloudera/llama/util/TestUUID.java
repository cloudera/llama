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
package com.cloudera.llama.util;

import junit.framework.Assert;
import org.junit.Test;

public class TestUUID {

  @Test
  public void testLUUID() throws Exception{
    java.util.UUID uuid = java.util.UUID.randomUUID();
    UUID UUID1 = new UUID(uuid);
    UUID UUID2 = new UUID(uuid);
    Assert.assertEquals(UUID1, UUID2);
    String s1 = UUID1.toString();
    UUID UUID3 = UUID.fromString(s1);
    Assert.assertEquals(UUID1, UUID3);
  }
}
