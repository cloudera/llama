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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import junit.framework.Assert;
import org.junit.Test;

import java.util.Map;

public class TestMetricUtil {

  public static class MyGauge implements Gauge<Integer> {
    private Integer value;

    public MyGauge(int value) {
      this.value = value;
    }

    @Override
    public Integer getValue() {
      return value;
    }
  }

  @Test
  public void testGaugeRegistration() {
    MetricRegistry mr = new MetricRegistry();
    Gauge<Integer> g1 = new MyGauge(1);
    MetricUtil.registerGauge(mr, "g", g1);
    Assert.assertEquals(1, mr.getGauges().entrySet().size());
    Map.Entry<String, Gauge> entry = mr.getGauges().entrySet().iterator().next();
    Assert.assertEquals("g", entry.getKey());
    Assert.assertEquals(1, entry.getValue().getValue());
    Gauge<Integer> g2 = new MyGauge(2);
    MetricUtil.registerGauge(mr, "g", g2);
    Assert.assertEquals(1, mr.getGauges().entrySet().size());
    entry = mr.getGauges().entrySet().iterator().next();
    Assert.assertEquals("g", entry.getKey());
    Assert.assertEquals(2, entry.getValue().getValue());
  }
}
