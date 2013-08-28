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

import com.cloudera.llama.am.impl.APIContractLlamaAM;
import com.cloudera.llama.am.impl.GangAntiDeadlockLlamaAM;
import com.cloudera.llama.am.impl.MultiQueueLlamaAM;
import com.cloudera.llama.am.impl.ParamChecker;
import org.apache.hadoop.conf.Configuration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class LlamaAM {
  public static final String PREFIX_KEY = "llama.am.";

  public static final String RM_CONNECTOR_CLASS_KEY = PREFIX_KEY + 
      "rm.connector.class";

  public static final String INITIAL_QUEUES_KEY =  PREFIX_KEY + 
      "initial.queues";

  public static final String GANG_ANTI_DEADLOCK_ENABLED_KEY = PREFIX_KEY +
      "gang.anti.deadlock.enabled";
  public static final boolean GANG_ANTI_DEADLOCK_ENABLED_DEFAULT = false;

  public static final String GANG_ANTI_DEADLOCK_NO_ALLOCATION_LIMIT_KEY =
      PREFIX_KEY + "gang.anti.deadlock.no.allocation.limit.ms";
  public static final long GANG_ANTI_DEADLOCK_NO_ALLOCATION_LIMIT_DEFAULT =
      30000;

  public static final String GANG_ANTI_DEADLOCK_BACKOFF_PERCENT_KEY =
      PREFIX_KEY + "gang.anti.deadlock.backoff.percent";
  public static final int GANG_ANTI_DEADLOCK_BACKOFF_PERCENT_DEFAULT =
      30;

  public static final String GANG_ANTI_DEADLOCK_BACKOFF_MIN_DELAY_KEY =
      PREFIX_KEY + "gang.anti.deadlock.backoff.min.delay.ms";
  public static final long GANG_ANTI_DEADLOCK_BACKOFF_MIN_DELAY_DEFAULT = 10000;

  public static final String GANG_ANTI_DEADLOCK_BACKOFF_MAX_DELAY_KEY =
      PREFIX_KEY + "gang.anti.deadlock.backoff.max.delay.ms";
  public static final long GANG_ANTI_DEADLOCK_BACKOFF_MAX_DELAY_DEFAULT = 30000;

  private static Configuration cloneConfiguration(Configuration conf) {
    Configuration clone = new Configuration(false);
    for (Map.Entry<String, String> entry : conf) {
      clone.set(entry.getKey(), entry.getValue());
    }
    return clone;
  }

  public static LlamaAM create(Configuration conf) throws LlamaAMException {
    conf = cloneConfiguration(conf);
    LlamaAM am = new MultiQueueLlamaAM(conf);
    if (conf.getBoolean(GANG_ANTI_DEADLOCK_ENABLED_KEY,
        GANG_ANTI_DEADLOCK_ENABLED_DEFAULT)) {
      am = new GangAntiDeadlockLlamaAM(conf, am);
    }
    return new APIContractLlamaAM(am);
  }

  private Configuration conf;
  
  protected LlamaAM(Configuration conf) {
    this.conf = ParamChecker.notNull(conf, "conf");  
  }
  
  public Configuration getConf() {
    return conf;
  } 
  
  public abstract void start() throws LlamaAMException;

  public abstract void stop();
  
  public abstract boolean isRunning();

  public abstract List<String> getNodes() throws LlamaAMException;

  public abstract void reserve(UUID reservationId, Reservation reservation)
      throws LlamaAMException;

  public UUID reserve(Reservation reservation) throws LlamaAMException {
    UUID reservationId = UUID.randomUUID();
    reserve(reservationId, reservation);
    return reservationId;
  }

  public abstract PlacedReservation getReservation(UUID reservationId)
      throws LlamaAMException;

  public abstract void releaseReservation(UUID reservationId) 
      throws LlamaAMException;

  public abstract List<UUID> releaseReservationsForClientId(UUID clientId)
    throws LlamaAMException;
  
  public abstract void addListener(LlamaAMListener listener);

  public abstract void removeListener(LlamaAMListener listener);

}
