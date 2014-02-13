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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestServerContextImpl {
  @Test
  public void testStart() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 2,2,2);

    try {
      impl.start();
    } finally {
      impl.stop();
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testStartStart() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 2,2,2);

    try {
      impl.start();
      impl.start();
    } finally {
      impl.stop();
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testStartStopStart() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 2,2,2);

    try {
      impl.start();
    } finally {
      impl.stop();
    }
    impl.start();
  }

  @Test(expected = IllegalStateException.class)
  public void testStopNoStart() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 2,2,2);
    impl.stop();
  }

  @Test
  public void testAsyncExecution() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 2, 2, 2);
    final AtomicInteger number = new AtomicInteger(0);
    int numTasks = 100;

    try {
      impl.start();

      // Submit a job.
      Callable<Void> task = new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          number.incrementAndGet();
          return null;
        }
      };

      List<Callable<Void>> tasks = Collections.nCopies(numTasks, task);
      for(Callable<Void> c : tasks) {
        Assert.assertTrue(impl.asyncExecution(c));
      }
    } finally {
      impl.stop();
    }
    Assert.assertTrue(number.intValue() == numTasks);
  }

  @Test
  public void testAsyncExecutionQueueRejections() throws Exception {
    int asyncThreads = 40;
    int queueSize = 40;
    final AtomicInteger number = new AtomicInteger(0);

    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", asyncThreads,
            queueSize, 2);
    try {
      impl.start();

      // Submit a job.
      Callable<Void> task = new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          number.incrementAndGet();
          Thread.sleep(Long.MAX_VALUE);
          return null;
        }
      };

      // Execute some in the threads and max it out.
      List<Callable<Void>> tasks = Collections.nCopies(asyncThreads, task);
      for(Callable<Void> c : tasks) {
        Assert.assertTrue(impl.asyncExecution(c));
      }

      // Add enough to the queue
      tasks = Collections.nCopies(queueSize, task);
      for (int i = 0; i < queueSize; i++) {
        Assert.assertTrue(impl.asyncExecution(tasks.get(i)));
      }

      // Now any tasks we queue should fail.
      Assert.assertFalse(impl.asyncExecution(task));
    } finally {
      impl.stop();
    }
    Assert.assertTrue(number.intValue() == queueSize);
  }

  class MyCallable implements Callable<Void> {

    private long expectedStartTime;
    private final int interval;

    MyCallable(int delay, int interval) {
      this.expectedStartTime = System.currentTimeMillis() + delay;
      this.interval = interval;
    }

    @Override
    public Void call() throws Exception {
      Assert.assertTrue(System.currentTimeMillis() >= expectedStartTime);
      expectedStartTime += interval;
      return null;
    }
  }
  @Test
  public void testAsyncExecutionDelay() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 50, 50, 2);
    try {
      impl.start();

      int numTasks = 50;
      for (int i = 0; i < numTasks; i++) {
        MyCallable c = new MyCallable(i, 0);
        Assert.assertTrue(impl.asyncExecution(c, i, TimeUnit.MILLISECONDS));
      }
    } finally {
      impl.stop();
    }
  }

  @Test
  public void testScheduleExecution() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 2, 2, 2);
    try {
      impl.start();

      final AtomicInteger number = new AtomicInteger(0);
      // Submit a job.
      Callable<Void> task = new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          number.incrementAndGet();
          return null;
        }
      };
      int interval = 10;
      ServerContext.Scheduled scheduled = impl.scheduleExecution(task, 0, interval,
          TimeUnit.MILLISECONDS);
      Assert.assertEquals(scheduled.getCallable(), task);

      // Make sure its running
      Thread.sleep(interval*2);
      Assert.assertTrue(number.intValue() >= 2);

      // Now cancel it and make sure its stopped.
      scheduled.cancel();
      Thread.sleep(interval);

      int currentNum = number.intValue();
      Thread.sleep(interval);
      Assert.assertEquals(number.intValue(), currentNum);
    } finally {
      impl.stop();
    }
  }

  @Test
  public void testScheduleExecutionDelay() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 50, 50, 2);
    try {
      impl.start();

      int interval = 10;
      int numTasks = 10;

      for (int i = 0; i < numTasks; i++) {
        MyCallable c = new MyCallable(i, 10);
        impl.scheduleExecution(c, i, interval, TimeUnit.MILLISECONDS);
      }

      // Let it run for at least two intervals.
      Thread.sleep(interval*2);
    } finally {
      impl.stop();
    }
  }

  @Test
  public void testRegisterGauge() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 50, 50, 2);
    impl.start();

    String key = impl.createMetricKey("Test", "ServerContext");
    Gauge guage = new Gauge() {
      @Override
      public Object getValue() {
        return this;
      }
    };
    impl.registerGauge(key, guage);
  }

  @Test
  public void testRegisterTimer() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 50, 50, 2);
    try {
      impl.start();

      String key = impl.createMetricKey("Test", "ServerContext");

      Timer timer1 = impl.registerTimer(key);
      Timer timer2 = impl.registerTimer(key);
      Assert.assertEquals(timer1, timer2);

      String key2 = impl.createMetricKey("Test", "ServerContext 2");
      Timer timer3 = impl.registerTimer(key2);
      Assert.assertNotSame(timer1, timer3);
    } finally {
      impl.stop();
    }
  }

  @Test
  public void testRegisterMeter() throws Exception {
    ServerContextImpl impl =
        new ServerContextImpl("llama", "Server Context", 50, 50, 2);
    try {
      impl.start();

      String key = impl.createMetricKey("Test", "ServerContext");

      Meter meter1 = impl.registerMeter(key);
      Meter meter2 = impl.registerMeter(key);
      Assert.assertEquals(meter1, meter2);

      String key2 = impl.createMetricKey("Test", "ServerContext 2");
      Meter meter3 = impl.registerMeter(key2);
      Assert.assertNotSame(meter1, meter3);
    } finally {
      impl.stop();
    }
  }
}
