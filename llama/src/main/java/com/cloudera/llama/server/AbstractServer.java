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

import com.codahale.metrics.MetricRegistry;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractServer implements Configurable {
  private final Logger log;
  private final String serverName;
  private Configuration llamaConf;
  private int runLevel = 0;
  private int exitCode = 0;

  protected AbstractServer(String serverName) {
    log = LoggerFactory.getLogger(getClass());
    this.serverName = serverName;
  }

  public String getServerName() {
    return serverName;
  }

  protected Logger getLog() {
    return log;
  }

  @Override
  public void setConf(Configuration conf) {
    llamaConf = conf;
  }

  @Override
  public Configuration getConf() {
    return llamaConf;
  }

  private volatile Exception transportException = null;

  // non blocking
  public synchronized void start() {
    if (runLevel != 0) {
      throw new RuntimeException("AbstractServer already started");
    }
    runLevel = 0;
    getLog().trace("Starting metrics");
    startMetrics();
    runLevel = 1;
    getLog().trace("Starting JMX");
    startJMX();
    runLevel = 2;
    getLog().trace("Starting service '{}'", serverName);
    startService();
    runLevel = 3;
    getLog().trace("Starting transport");
    Thread transportThread = new Thread(getClass().getSimpleName()) {
      @Override
      public void run() {
        try {
          startTransport();
        } catch (Exception ex) {
          transportException = ex;
          getLog().error(ex.toString(), ex);
        }
      }
    };
    transportThread.start();
    while (getAddressPort() == 0) {
      if (transportException != null) {
        stop();
        throw new RuntimeException(transportException);
      }
      try {
        Thread.sleep(1);
      } catch (InterruptedException ex) {
        throw new RuntimeException(ex);
      }
    }
    runLevel = 4;
    getLog().info("Server listening at '{}:{}'", getAddressHost(),
        getAddressPort());
    getLog().info("Llama started!");
  }

  public void shutdown(int exitCode) {
    this.exitCode = exitCode;
    getLog().debug("Initiating shutdown");
    stop();
  }

  public int getExitCode() {
    return exitCode;
  }

  public void stop() {
    if (runLevel >= 4) {
      try {
        getLog().trace("Stopping transport");
        stopTransport();
      } catch (Throwable ex) {
        getLog().warn("Failed to stop transport server: {}", ex.toString(), ex);
      }
    }
    if (runLevel >= 3) {
      try {
        getLog().trace("Stopping service '{}'", serverName);
        stopService();
      } catch (Throwable ex) {
        getLog().warn("Failed to stop service '{}': {}", serverName,
            ex.toString(), ex);
      }
    }
    if (runLevel >= 2) {
      try {
        getLog().trace("Stopping JMX");
        stopJMX();
      } catch (Throwable ex) {
        getLog().warn("Failed to stop JMX: {}", ex.toString(), ex);
      }
    }
    if (runLevel >= 1) {
      try {
        getLog().trace("Stopping metrics");
        stopMetrics();
      } catch (Throwable ex) {
        getLog().warn("Failed to stop Metrics: {}", ex.toString(), ex);
      }
    }
    getLog().info("Llama shutdown!");
    runLevel = -1;
  }

  private MetricRegistry metrics = new MetricRegistry();

  protected void startMetrics() {
    metrics = new MetricRegistry();
  }

  protected MetricRegistry getMetricRegistry() {
    return metrics;
  }

  protected void stopMetrics() {
    metrics = null;
  }

  protected void startJMX() {
  }

  protected void stopJMX() {
  }

  protected abstract void startService();

  protected abstract void stopService();

  //blocking
  protected abstract void startTransport();

  protected abstract void stopTransport();

  public abstract String getAddressHost();

  public abstract int getAddressPort();

}
