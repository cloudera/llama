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
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServerContextImpl implements ServerContext {
  private static final Logger LOG = LoggerFactory.getLogger(
      ServerContextImpl.class);

  private String prefix;
  private String name;
  private int asyncMaxQueued;
  private int asyncThreads;
  private int schedulerThreads;
  private MetricRegistry metricRegistry;
  private DelayQueue<DelayedRunnable> asyncQueue;
  private ThreadPoolExecutor asyncExecutor;
  private ScheduledExecutorService scheduledExecutor;
  private Timer schedulerExecutorDelayTimer;
  private Timer asyncExecutorDelayTimer;

  enum ServerState {
    NOT_STARTED,
    STARTED,
    STOPPED;
  };
  private volatile ServerState state; // 0 not-started, 1 started, 2 stopped

  /**
   * Server context implementation.
   * @param prefix prefix to use for the metrics.
   * @param name name of the server context, mostly used for logging and
   *             naming thread pools, etc.
   * @param asyncThreads Maximum number of threads used to process async jobs.
   * @param asyncMaxQueued Max number of jobs queued in job queue while async
   *                       threads are busy.
   * @param schedulerThreads number of core threads to process scheduled jobs.
   */
  public ServerContextImpl(String name, String prefix, int asyncThreads,
      int asyncMaxQueued, int schedulerThreads) {
    ParamChecker.notNull(prefix, "prefix");
    ParamChecker.notEmpty(name, "name");
    ParamChecker.greaterThan(asyncThreads, 0, "asyncThreads");
    ParamChecker.greaterEqualZero(asyncMaxQueued, "asyncMaxQueued");
    ParamChecker.greaterThan(schedulerThreads, 0, "schedulerThreads");
    if (!prefix.isEmpty() && !prefix.endsWith(".")) {
      prefix = prefix + ".";
    }
    this.prefix = prefix;
    this.name = name;
    this.asyncMaxQueued = asyncMaxQueued;
    this.asyncThreads = asyncThreads;
    this.schedulerThreads = schedulerThreads;
    state = ServerState.NOT_STARTED;
  }

  @SuppressWarnings("unchecked")
  public synchronized void start() {
    if (state == ServerState.STARTED) {
      throw new IllegalStateException("Already started");
    }
    if (state == ServerState.STOPPED) {
      throw new IllegalStateException("Already started and stopped");
    }
    metricRegistry = new MetricRegistry();
    asyncQueue = new DelayQueue<DelayedRunnable>();
    ThreadFactory threadFactory = new NamedThreadFactory("ServerContext(" +
        name + ")");
    //funny downcasting and upcasting because javac gets goofy here
    asyncExecutor = new ThreadPoolExecutor(asyncThreads, asyncThreads, 0,
        TimeUnit.SECONDS, (BlockingQueue<Runnable>) (BlockingQueue) asyncQueue,
        threadFactory);
    asyncExecutor.prestartAllCoreThreads();

    scheduledExecutor = new ScheduledThreadPoolExecutor(schedulerThreads,
        threadFactory);
    LOG.debug("'{}' - Starting with asyncThreads '{}', asyncMaxQueued '{}', " +
        "schedulerThreads '{}'", name, asyncThreads, asyncMaxQueued,
        schedulerThreads);
    state = ServerState.STARTED;
    schedulerExecutorDelayTimer = registerTimer(createMetricKey(name,
        "schedulerExecutor.delay"));
    asyncExecutorDelayTimer = registerTimer(createMetricKey(name,
        "asyncExecutor.delay"));
  }

  private void stopExecutorService(ExecutorService executorService) {
    try {
      executorService.shutdown();
      if (!executorService.awaitTermination(2000, TimeUnit.MILLISECONDS)) {
        LOG.warn("'{}' - Stopping, forcing shutdown of '{}'",
            name, executorService.getClass().getSimpleName());
        executorService.shutdownNow();
      }
    } catch (Exception ex) {
      LOG.error("'{}' - Stopping, could not shutdown '{}': {}",
          name, executorService.getClass().getSimpleName(), ex.toString(), ex);
    }
  }

  public synchronized void stop() {
    checkIsRunning();
    state = ServerState.STOPPED;
    LOG.debug("'{}' - Stopping", name);
    metricRegistry = null;
    stopExecutorService(asyncExecutor);
    stopExecutorService(scheduledExecutor);
  }

  private void checkIsRunning() {
    if (state != ServerState.STARTED) {
      throw new IllegalStateException("Not running");
    }
  }

  public synchronized MetricRegistry getMetricRegistry() {
    checkIsRunning();
    return metricRegistry;
  }

  private class AsyncRunnable extends DelayedRunnable {
    private Callable<Void> callable;
    private long expectedExecution;
    private Timer timer;
    private long interval;

    /**
     * The async runnable that gets passed to the thread pool executor.
     * @param callable The main caller task.
     * @param delay The task will not start until this time in milliseconds
     * @param interval The task will repeat after this time in milliseconds
     * @param timer The Metrics timer to track statistics about execution.
     */
    public AsyncRunnable(Callable<Void> callable, long delay, long interval,
        Timer timer) {
      super(delay);
      this.callable = callable;
      this.timer = timer;
      this.interval = interval;
      expectedExecution = Clock.currentTimeMillis() + delay;
    }

    @Override
    public void run() {
      try {
        LOG.debug("'{}' - Executing '{}'", name, callable);
        long actualExecution = Clock.currentTimeMillis();
        long delay = actualExecution - expectedExecution;
        delay = (delay >= 0) ? delay : 0;
        timer.update(delay, TimeUnit.MILLISECONDS);
        callable.call();
      } catch (InterruptedException ex) {
        LOG.warn("'{}' - Interrupted: {}", name, ex.toString(),
            ex);
      } catch (Throwable ex) {
        LOG.error("'{}' - Error during '{}' execution : {}", name,
            callable, ex.toString(), ex);
      } finally {
        expectedExecution += interval;
      }
    }
  }

  private static class ScheduledImpl implements Scheduled {
    private Callable<Void> callable;
    private ScheduledFuture future;

    public ScheduledImpl(Callable<Void> callable, ScheduledFuture future) {
      this.callable = callable;
      this.future = future;
    }

    @Override
    public Callable<Void> getCallable() {
      return callable;
    }

    @Override
    public void cancel() {
      future.cancel(true);
    }
  }

  @Override
  public Scheduled scheduleExecution(Callable<Void> callable,
      long firstExecutionDelay, long executionInterval, TimeUnit timeUnit) {
    checkIsRunning();
    ParamChecker.notNull(callable, "callable");
    ParamChecker.greaterEqualZero(firstExecutionDelay, "firstExecutionDelay");
    ParamChecker.greaterThan(executionInterval, 0, "executionInterval");
    ParamChecker.notNull(timeUnit, "timeUnit");
    if (timeUnit.convert(executionInterval, TimeUnit.MILLISECONDS) < 10) {
      LOG.warn("'{}' - scheduleExecution({}, {}, {}, {}) interval below 10ms",
          name, callable, firstExecutionDelay, executionInterval, timeUnit);
    } else {
      LOG.debug("'{}' - scheduleExecution({}, {}, {}, {})", name, callable,
          firstExecutionDelay, executionInterval, timeUnit);
    }
    ScheduledFuture future = scheduledExecutor.scheduleAtFixedRate(
        new AsyncRunnable(callable,
            TimeUnit.MILLISECONDS.convert(firstExecutionDelay, timeUnit),
            TimeUnit.MILLISECONDS.convert(executionInterval, timeUnit),
            schedulerExecutorDelayTimer),
        firstExecutionDelay, executionInterval, timeUnit);
    return new ScheduledImpl(callable, future);
  }

  @Override
  public boolean asyncExecution(Callable<Void> callable) {
    return asyncExecution(callable, 0, TimeUnit.MILLISECONDS);
  }

  @Override
  public boolean asyncExecution(Callable<Void> callable, long executionDelay,
      TimeUnit timeUnit) {
    checkIsRunning();
    ParamChecker.notNull(callable, "callable");
    ParamChecker.greaterEqualZero(executionDelay, "executionDelay");
    ParamChecker.notNull(timeUnit, "timeUnit");
    LOG.debug("'{}' - asyncExecution({}, {}, {})", name, callable, executionDelay,
        timeUnit);
    boolean queued = false;
    if (asyncQueue.size() < asyncMaxQueued) {
      // We have to add it to the queue instead of directly giving it to the
      // asyncExecutor, otherwise the executor will start executing it right
      // away if there is a thread available breaking the contract for delay.
      asyncQueue.add(new AsyncRunnable(callable,
              TimeUnit.MILLISECONDS.convert(executionDelay, timeUnit), 0,
              asyncExecutorDelayTimer));
      queued = true;
    } else {
      LOG.warn("'{}' - rejected asyncExecution({}, {}, {}), queue over limit",
          name, callable, executionDelay, timeUnit);
    }
    return queued;
  }

  // Metric

  private static class ExactMetricFilter implements MetricFilter {
    private String key;

    public ExactMetricFilter(String key) {
      this.key = key;
    }

    @Override
    public boolean matches (String name, Metric metric){
      return name.equals(key);
    }
  }

  private enum MetricType { TIMER, METER, GAUGE }

  @SuppressWarnings("unchecked")
  private <T extends Metric> T findMetric(MetricType type, String key) {
    T metric = null;
    switch (type) {
    case TIMER:
      SortedMap<String, Timer> timersMap = metricRegistry.getTimers(
          new ExactMetricFilter(key));
      if (!timersMap.isEmpty()) {
        metric = (T) timersMap.values().iterator().next();
      }
      break;
    case METER:
      SortedMap<String, Meter> metersMap = metricRegistry.getMeters(
          new ExactMetricFilter(key));
      if (!metersMap.isEmpty()) {
        metric = (T) metersMap.values().iterator().next();
      }
      break;
    case GAUGE:
      SortedMap<String, Gauge> gaugesMap = metricRegistry.getGauges(
          new ExactMetricFilter(key));
      if (!gaugesMap.isEmpty()) {
        metric = (T) gaugesMap.values().iterator().next();
      }
      break;
    }
    return metric;
  }


  /**
   * We need this class so that we use the same codehale metrics guage for the
   * same AM. The AM could be restarted and if we do not use the same guage
   * all the metrics would be screwed up on the JMX console, etc.
   */
  private static class ProxyGauge implements Gauge {
    private volatile Gauge gauge;

    public ProxyGauge(Gauge gauge) {
      setGauge(gauge);
    }

    public void setGauge(Gauge gauge) {
      this.gauge = gauge;
    }

    @Override
    public Object getValue() {
      return gauge.getValue();
    }
  }

  @Override
  public String createMetricKey(String component, String key) {
    ParamChecker.notEmpty(key, "key");
    ParamChecker.notEmpty(component, "component");
    return FastFormat.format("[{}].{}", component, key);
  }

  private String getFullMetricKey(String key, MetricType type) {
    return prefix + key + "." + type.toString().toLowerCase();
  }

  @Override
  public synchronized void registerGauge(String key, Gauge gauge) {
    checkIsRunning();
    ParamChecker.notEmpty(key, "key");
    ParamChecker.notNull(gauge, "gauge");
    key = getFullMetricKey(key, MetricType.GAUGE);
    LOG.debug("'{}' - registerGauge({})", name, key);
    ProxyGauge proxyGauge = findMetric(MetricType.GAUGE, key);
    if (proxyGauge == null) {
      proxyGauge = new ProxyGauge(gauge);
      metricRegistry.register(key, proxyGauge);
    } else {
      proxyGauge.setGauge(gauge);
    }
  }

  private Timer createTimer() {
    return new Timer(new SlidingTimeWindowReservoir(60, TimeUnit.SECONDS));
  }

  @Override
  public synchronized Timer registerTimer(String key) {
    checkIsRunning();
    ParamChecker.notEmpty(key, "key");
    key = getFullMetricKey(key, MetricType.TIMER);
    LOG.debug("'{}' - registerTimer({})", name, key);
    Timer timer = findMetric(MetricType.TIMER, key);
    if (timer == null) {
      timer = metricRegistry.register(key, createTimer());
    }
    return timer;
  }

  @Override
  public synchronized Meter registerMeter(String key) {
    checkIsRunning();
    ParamChecker.notEmpty(key, "key");
    key = getFullMetricKey(key, MetricType.METER);
    LOG.debug("'{}' - registerMeter({})", name, key);
    Meter meter = findMetric(MetricType.METER, key);
    if (meter == null) {
      meter = metricRegistry.register(key, new Meter());
    }
    return meter;
  }
}
