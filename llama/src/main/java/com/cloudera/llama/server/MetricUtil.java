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

import com.cloudera.llama.util.FastFormat;
import com.cloudera.llama.thrift.TUniqueId;
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
import java.util.concurrent.TimeUnit;

public class MetricUtil {
  private static final Logger LOG = LoggerFactory.getLogger("llama.metric");

  private static Timer createTimer() {
    return new Timer(new SlidingTimeWindowReservoir(60, TimeUnit.SECONDS));
  }

  private static class ChangeableGauge implements Gauge {
    private Gauge gauge;

    public ChangeableGauge(Gauge gauge) {
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

  public static synchronized void registerGauge(MetricRegistry metricReg,
      final String key, Gauge gauge) {
    if (metricReg != null) {
      try {
        // As it is not possible to re-register a metric (gauge in this case)
        // we are registering a proxy gauge and on registration we update the
        // proxy gauge to proxy to the new gauge.
        // we need to do this for entities that come and go, like AMs not in
        // the core list. There is not need to do that for meters and timers
        // as they are timebased and they will zero out.
        SortedMap<String, Gauge> existingGauges =
            metricReg.getGauges(new MetricFilter() {
          @Override
          public boolean matches(String name, Metric metric) {
            return name.equals(key);
          }
        });
        if (existingGauges.isEmpty()) {
          gauge = new ChangeableGauge(gauge);
          metricReg.register(key, gauge);
        } else {
          ChangeableGauge cGauge = (ChangeableGauge)
              existingGauges.values().iterator().next();
          cGauge.setGauge(gauge);
        }
      } catch (IllegalArgumentException ex) {
        //NOP ignoring re-registrations;
      }
    }
  }

  public static void registerTimer(MetricRegistry metricReg, String key) {
    if (metricReg != null) {
      try {
        metricReg.register(key, createTimer());
      } catch (IllegalArgumentException ex) {
        //NOP ignoring re-registrations;
      }
    }
  }

  public static void registerMeter(MetricRegistry metricReg, String key) {
    if (metricReg != null) {
      try {
        metricReg.register(key, new Meter());
      } catch (IllegalArgumentException ex) {
        //NOP ignoring re-registrations;
      }
    }
  }

  public static void time(MetricRegistry metricReg, String key, long msTime,
      Object logContext) {
    if (metricReg != null) {
      metricReg.timer(key).update(msTime, TimeUnit.MILLISECONDS);
    }
  }

  public static void meter(MetricRegistry metricReg, String key, int count) {
    if (metricReg != null) {
      metricReg.meter(key).mark(count);
    }
  }

  public static class LogContext {
    private String messagePattern;
    private Object[] args;

    //Lazy format triggered by toString()
    public LogContext(String messagePattern, Object... args) {
      this.messagePattern = messagePattern;
      this.args = args;
    }

    private static Object[] massageArgs(Object[] args) {
      for (int i = 0; i < args.length; i++) {
        if (args[i] instanceof TUniqueId) {
          args[i] = TypeUtils.toUUID((TUniqueId) args[i]);
        }
      }
      return args;
    }

    public String toString() {
      return FastFormat.format(messagePattern, massageArgs(args));
    }
  }
}
