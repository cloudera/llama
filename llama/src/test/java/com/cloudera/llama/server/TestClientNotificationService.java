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
package com.cloudera.llama.server;

import com.cloudera.llama.am.AMServerConfiguration;
import com.cloudera.llama.util.UUID;
import junit.framework.Assert;
import org.junit.Test;

public class TestClientNotificationService {

  public static class MyUnregisterListener implements
      ClientNotificationService.UnregisterListener {

    private boolean called;

    @Override
    public void onUnregister(UUID handle) {
      called = true;
    }
  }

  @Test
  public void testRegisterNewClientIdNewCallback() throws Exception {
    MyUnregisterListener ul = new MyUnregisterListener();
    ClientNotificationService cns = new ClientNotificationService(
        new AMServerConfiguration(), null, null, ul);
    cns.start();
    try {
      UUID handle = cns.register("c1", "h", 0);
      Assert.assertNotNull(handle);
      Assert.assertTrue(cns.unregister(handle));
      Assert.assertTrue(ul.called);
    } finally {
      cns.stop();
    }
  }

  @Test
  public void testRegisterNewClientIdExistingCallback() throws Exception {
    MyUnregisterListener ul = new MyUnregisterListener();
    ClientNotificationService cns = new ClientNotificationService(
        new AMServerConfiguration(), null, null, ul);
    cns.start();
    try {
      UUID handle1 = cns.register("c1", "h", 0);
      Assert.assertNotNull(handle1);
      UUID handle2 = cns.register("c2", "h", 0);
      Assert.assertNotSame(handle1 ,handle2);
      Assert.assertTrue(ul.called);
      ul.called = false;
      Assert.assertTrue(cns.unregister(handle2));
      Assert.assertTrue(ul.called);
    } finally {
      cns.stop();
    }
  }

  @Test
  public void testRegisterExistingClientIdExistingCallbackSameHandle()
      throws Exception {
    MyUnregisterListener ul = new MyUnregisterListener();
    ClientNotificationService cns = new ClientNotificationService(
        new AMServerConfiguration(), null, null, ul);
    cns.start();
    try {
      UUID handle1 = cns.register("c1", "h", 0);
      Assert.assertNotNull(handle1);
      UUID handle2 = cns.register("c1", "h", 0);
      Assert.assertEquals(handle1, handle2);
      Assert.assertFalse(ul.called);
      Assert.assertTrue(cns.unregister(handle2));
      Assert.assertTrue(ul.called);
    } finally {
      cns.stop();
    }
  }

  @Test(expected = ClientRegistryException.class)
  public void testRegisterExistingClientIdExistingCallbackDifferentHandle()
      throws Exception {
    MyUnregisterListener ul = new MyUnregisterListener();
    ClientNotificationService cns = new ClientNotificationService(
        new AMServerConfiguration(), null, null, ul);
    cns.start();
    try {
      UUID handle1 = cns.register("c1", "h1", 0);
      Assert.assertNotNull(handle1);
      UUID handle2 = cns.register("c2", "h2", 0);
      Assert.assertNotNull(handle2);
      Assert.assertNotSame(handle1, handle2);
      cns.register("c1", "h2", 0);
    } finally {
      Assert.assertFalse(ul.called);
      cns.stop();
    }
  }

  @Test(expected = ClientRegistryException.class)
  public void testRegisterExistingClientIdNonExistingCallback()
      throws Exception {
    MyUnregisterListener ul = new MyUnregisterListener();
    ClientNotificationService cns = new ClientNotificationService(
        new AMServerConfiguration(), null, null, ul);
    cns.start();
    try {
      UUID handle1 = cns.register("c1", "h1", 0);
      Assert.assertNotNull(handle1);
      cns.register("c1", "h2", 0);
    } finally {
      Assert.assertFalse(ul.called);
      cns.stop();
    }
  }

}
