/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional inforAMtion
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you AMy not use this file except in compliance
 * with the License.  You AMy obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.llama.am;

import com.cloudera.llama.am.impl.APIContractEnforcerLlamaAM;
import com.cloudera.llama.am.impl.MultiQueueLlamaAM;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ReflectionUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class LlamaAM {
  public static final String PREFIX_KEY = "llama.am.";
  public static final String CLASS_KEY = PREFIX_KEY + "class";
  public static final String INITIAL_QUEUES_KEY =  PREFIX_KEY + 
      "initial.queues";

  private static Configuration cloneConfiguration(Configuration conf) {
    Configuration clone = new Configuration(false);
    for (Map.Entry<String, String> entry : conf) {
      clone.set(entry.getKey(), entry.getValue());
    }
    return clone;
  }

  public static LlamaAM create(Configuration conf) throws LlamaAMException {
    conf = cloneConfiguration(conf);
    if (conf.get(CLASS_KEY) == null) {
      throw new IllegalArgumentException("LlamaAM class not set");
    }
    boolean multi = conf.getBoolean(MultiQueueLlamaAM.MULTI_CREATE_KEY, true);
    String classKey = (multi) ? MultiQueueLlamaAM.SINGLE_QUEUE_AM_CLASS_KEY
                              : CLASS_KEY;
    Class<? extends LlamaAM> defaultClass = (multi) ? MultiQueueLlamaAM.class
                                                    : null;
    Class<? extends LlamaAM> klass = conf.getClass(classKey, defaultClass, 
        LlamaAM.class);
    LlamaAM am = ReflectionUtils.newInstance(klass, conf);
    return (multi) ? new APIContractEnforcerLlamaAM(am) : am;
  }

  public abstract Configuration getConf();
  
  public abstract void start() throws LlamaAMException;

  public abstract void stop();

  public abstract List<String> getNodes() throws LlamaAMException;
  
  public abstract UUID reserve(Reservation reservation) throws LlamaAMException;

  public abstract PlacedReservation getReservation(UUID reservationId)
      throws LlamaAMException;

  public abstract void releaseReservation(UUID reservationId) 
      throws LlamaAMException;

  public abstract void releaseReservationsForClientId(UUID clientId)
    throws LlamaAMException;
  
  public abstract void addListener(LlamaAMListener listener);

  public abstract void removeListener(LlamaAMListener listener);

}
