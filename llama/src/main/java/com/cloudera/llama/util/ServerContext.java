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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * The server context that provides various capabilities like
 * - A common threadpool for callable executions,
 * - Delayed execution
 * - Codehale metrics,
 */
public interface ServerContext {

  /**
   * Returned when scheduling a callable for regular execution via
   * {@link #scheduleExecution}.
   * The registrar of the callable should hold this object to cancel regular
   * execution in the future.
   */
  public interface Scheduled {

    public Callable<Void> getCallable();

    public void cancel();
  }

  /**
   * Schedule an exection with a proper delay. The returned structure has a
   * method to cancel it.
   * @param callable The main callable that gets executed.
   * @param firstExecutionDelay The initial delay until this task gets started.
   * @param executionInterval The time period to wait until the next instance of
   *                          this task gets invoked.
   * @param timeUnit time unit describing the metric the above two parameters.
   * @return Returns an object which could be used to cancel this scheduled job.
   */
  public Scheduled scheduleExecution(Callable<Void> callable,
      long firstExecutionDelay, long executionInterval, TimeUnit timeUnit);

  // AsyncExecution

  /**
   * Async Execution adds the callable to a queue and then a threadpool service
   * picks it up as when the threads are available for processing.
   * @param callable The main callable that gets executed.
   * @return Returns false if the queue is full and cannot accept any more jobs.
   */
  public boolean asyncExecution(Callable<Void> callable);

  /**
   * Async exection with a delay. Guarantees that the task will not be started
   * until the executionDelay time has passed.
   * @param callable The main callable that gets executed.
   * @param executionDelay the time delay until this task is started.
   * @param timeUnit unit describing the execution delay.
   * @return Returns false if the queue is full and cannot accept any more jobs.
   */
  public boolean asyncExecution(Callable<Void> callable, long executionDelay,
      TimeUnit timeUnit);

  // Metrics

  /**
   * Returns a key which can be used for creating the metrics. The key must be
   * unique in the system.
   * @param component component part of the key
   * @param key the value of the key.
   * @return returns a formatted key containing the component and key parts.
   */
  public String createMetricKey(String component, String key);

  /**
   * Register a guage in the system for the given key.
   * @param key key created using {@link #createMetricKey}.
   * @param gauge Guage associated with the key.
   */
  public void registerGauge(final String key, Gauge gauge);

  /**
   * Register a timer for the given key and return it.
   * @param key the value of the key.
   * @return returns the timer object.
   */
  public Timer registerTimer(final String key);

  /**
   * Registers a meter for the given key and return it.
   * @param key the value of the key.
   * @return returns the meter object.
   */
  public Meter registerMeter(final String key);
}
