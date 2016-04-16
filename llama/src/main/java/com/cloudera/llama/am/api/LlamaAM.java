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
package com.cloudera.llama.am.api;

import com.cloudera.llama.am.cache.ResourceCache;
import com.cloudera.llama.am.impl.APIContractLlamaAM;
import com.cloudera.llama.am.impl.ExpansionReservationsLlamaAM;
import com.cloudera.llama.am.impl.GangAntiDeadlockLlamaAM;
import com.cloudera.llama.am.impl.MultiQueueLlamaAM;
import com.cloudera.llama.am.spi.RMConnector;
import com.cloudera.llama.am.yarn.YarnRMConnector;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.util.ParamChecker;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.MetricRegistry;
import org.apache.hadoop.conf.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * <code>LlamaAM</code> allows to acquire resources from a Hadoop Yarn cluster
 * at very low latencies, suitable for real time processing. These resources are
 * used out of band from the Hadoop.
 * <p/>
 * <code>LlamaAM</code> is a Yarn Application Master providing resource
 * management capabilities not supported natively by Hadoop Yarn. For example:
 * <p/>
 * <ul>
 *   <li>Gang scheduling</li>
 *   <li>Multiplexing reservation requests to different Yarn queues</li>
 *   <li>Expansion of reservations</li>
 *   <li>Reuse (via caching) of resource allocations</li>
 * </ul>
 * Llama uses an {@link RMConnector} implementation, {@link YarnRMConnector} to
 * interact with the Hadoop cluster.
 */
public abstract class LlamaAM {
  public static final String PREFIX_KEY = "llama.am.";

  public static final String METRIC_PREFIX = "llama.am.";

  public static final String CLUSTER_ID = PREFIX_KEY + "cluster.id";
  public static final String CLUSTER_ID_DEFAULT = "llama";

  public static final String RM_CONNECTOR_CLASS_KEY = PREFIX_KEY +
      "rm.connector.class";

  public static final String RM_CONNECTOR_RECYCLE_INTERVAL_KEY =
      PREFIX_KEY + "rm.connector.recycle.interval.mins";
  /** Default recycle interval level for rm connectors. This is overridden
   * by yarn configuration whenever present. */
  public static long RM_CONNECTOR_RECYCLE_INTERVAL_DEFAULT =
      20*60; // 20 hours.

  public static final String CORE_QUEUES_KEY = PREFIX_KEY +
      "core.queues";

  public static final String GANG_ANTI_DEADLOCK_ENABLED_KEY = PREFIX_KEY +
      "gang.anti.deadlock.enabled";
  public static final boolean GANG_ANTI_DEADLOCK_ENABLED_DEFAULT = true;

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

  public static final String CACHING_ENABLED_KEY =
      PREFIX_KEY + "cache.enabled";
  public static final boolean CACHING_ENABLED_DEFAULT = true;

  public static final String THROTTLING_ENABLED_KEY =
      PREFIX_KEY + "throttling.enabled";
  public static final boolean THROTTLING_ENABLED_DEFAULT = true;

  public static final String QUEUE_AM_EXPIRE_KEY =
      PREFIX_KEY + "queue.expire.ms";
  public static final int QUEUE_AM_EXPIRE_DEFAULT = 5 * 60 * 1000;

  public static final String NORMALIZING_ENABLED_KEY =
      PREFIX_KEY + "resource.normalizing.enabled";
  public static final boolean NORMALIZING_ENABLED_DEFAULT = true;

  public static final String NORMALIZING_STANDARD_MBS_KEY =
      PREFIX_KEY + "resource.normalizing.standard.mbs";
  public static final int NORMALIZING_SIZE_MBS_DEFAULT = 1024;

  public static final String NORMALIZING_STANDARD_VCORES_KEY =
      PREFIX_KEY + "resource.normalizing.standard.vcores";
  public static final int NORMALIZING_SIZE_VCORES_DEFAULT = 1;

  public static final String EVICTION_POLICY_CLASS_KEY =
      PREFIX_KEY + "cache.eviction.policy.class";
  public static final Class EVICTION_POLICY_CLASS_DEFAULT =
      ResourceCache.TimeoutEvictionPolicy.class;

  public static final String EVICTION_RUN_INTERVAL_KEY =
      PREFIX_KEY + "cache.eviction.run.interval.timeout.ms";
  public static final int EVICTION_RUN_INTERVAL_DEFAULT = 5000;

  public static final String EVICTION_IDLE_TIMEOUT_KEY =
      PREFIX_KEY + "cache.eviction.timeout.policy.idle.timeout.ms";
  public static final int EVICTION_IDLE_TIMEOUT_DEFAULT = 30000;

  private static Configuration cloneConfiguration(Configuration conf) {
    Configuration clone = new Configuration(false);
    for (Map.Entry<String, String> entry : conf) {
      clone.set(entry.getKey(), entry.getValue());
    }
    return clone;
  }

  /**
   * Creates a <code>LlamaAM</code> instance using the provided configuration.
   *
   * @param conf configuration for the LlamaAM instance. This configuration is
   * propagated to the underlying components (notably the {@link RMConnector}
   * instances).
   *
   * @return A new <code>LlamaAM</code> instance based on the provided
   * configuration.
   * @throws LlamaException thrown if the <code>LlamaAM</code> could not be
   * created.
   */
  public static LlamaAM create(Configuration conf)
      throws LlamaException {
    conf = cloneConfiguration(conf);
    LlamaAM am = new MultiQueueLlamaAM(conf);
    if (conf.getBoolean(GANG_ANTI_DEADLOCK_ENABLED_KEY,
        GANG_ANTI_DEADLOCK_ENABLED_DEFAULT)) {
      am = new GangAntiDeadlockLlamaAM(conf, am);
    }
    am = new ExpansionReservationsLlamaAM(am);
    return new APIContractLlamaAM(am);
  }

  private MetricRegistry metricRegistry;
  private Configuration conf;

  protected LlamaAM(Configuration conf) {
    this.conf = ParamChecker.notNull(conf, "conf");
  }

  /**
   * Sets the {@link MetricRegistry} instance for the <code>LlamaAM</code>.
   *
   * @param metricRegistry the {@link MetricRegistry} instance.
   */
  public void setMetricRegistry(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
  }

  /**
   * Gets the {@link MetricRegistry} instance of the <code>LlamaAM</code>.
   *
   * @return the {@link MetricRegistry} instance of the <code>LlamaAM</code>.
   */
  protected MetricRegistry getMetricRegistry() {
    return metricRegistry;
  }

  /**
   * Gets the configuration of the <code>LlamaAM</code> instance.
   * @return the configuration of the <code>LlamaAM</code> instance.
   */
  public Configuration getConf() {
    return conf;
  }

  /**
   * Starts the <code>LlamaAM</code> instance.
   *
   * @throws LlamaException thrown if an error occurs while starting the
   * instance.
   */
  public abstract void start() throws LlamaException;

  /**
   * Stops the <code>LlamaAM</code> instance.
   *
   */
  public abstract void stop();

  /**
   * Returns if the <code>LlamaAM</code> instance is running or not.
   * @return <code>true</code> if it is running, <code>false</code> otherwise.
   */
  public abstract boolean isRunning();

  /**
   * Returns a list with all the cluster node names.
   *
   * @return a list with all the cluster node names.
   * @throws LlamaException thrown if an error occurs while retrieving the
   * cluster node names.
   */
  public abstract List<NodeInfo> getNodes() throws LlamaException;

  /**
   * Places a reservation in the <code>LlamaAM</code>.
   *
   * @param reservationId the UUID for the reservation.
   * @param reservation the reservation.
   * @throws LlamaException thrown if there was an error placing the reservation.
   */
  public abstract void reserve(UUID reservationId, Reservation reservation)
      throws LlamaException;

  /**
   * Convenience method to place a reservation that creates a UUID for it.
   * <p/>
   * This method delegates to the {@link #reserve(UUID, Reservation)} method.
   *
   * @param reservation the reservation.
   * @return the UUID created for the reservation.
   * @throws LlamaException thrown if there was an error placing the reservation.
   */
  public UUID reserve(Reservation reservation)
      throws LlamaException {
    UUID id = UUID.randomUUID();
    reserve(id, reservation);
    return id;
  }

  /**
   * Places an expansion for a reservation in the <code>LlamaAM</code>.
   * <p/>
   * A placed expansion is handled as a {@link PlacedReservation}.
   *
   * @param expansionId the UUID for the expansion.
   * @param expansion the expansion.
   * @throws LlamaException thrown if there was an error placing the expansion.
   */
  public void expand(UUID expansionId, Expansion expansion)
      throws LlamaException {
    throw new UnsupportedOperationException();
  }

  /**
   * Convenience method to place an expansion for a reservation that creates a
   * UUID for it.
   * <p/>
   * This method delegates to the {@link #expand(UUID, Expansion)} method.
   * <p/>
   * A placed expansion is handled as a {@link PlacedReservation}.
   *
   * @param expansion the expansion.
   * @return the UUID created for the expansion.
   * @throws LlamaException thrown if there was an error placing the expansion.
   */
  public UUID expand(Expansion expansion)
      throws LlamaException {
    UUID id = UUID.randomUUID();
    expand(id, expansion);
    return id;
  }

  /**
   * Returns the placed reservation or placed expansion associated to the given
   * UUID.
   *
   * @param reservationId the UUID of the reservation or expansion to retrieve.
   * @return the placed reservation or expansion associated with the given UUID
   * or <code>NULL</code> if none.
   * @throws LlamaException thrown if an error occurred while retrieving the
   * placed reservation or expansion.
   */
  public abstract PlacedReservation getReservation(UUID reservationId)
      throws LlamaException;

  /**
   * This UUID constant is to be used by admin calls to release methods. It
   * has to be used within a {@link #doAsAdmin(Callable)} call.
   */
  public static final UUID WILDCARD_HANDLE = UUID.randomUUID();

  /**
   * Releases the placed reservation or expansion associated with the given
   * UUID.
   *
   * @param handle handle used to place the reservation.
   * @param reservationId the UUID of the placed reservation or expansion to
   * release.
   * @param doNotCache indicates if the resources of the reservation or
   * expansion being released should be cached  (if caching is enabled) or not.
   * @return the placed reservation or expansion being released, or
   * <code>NULL</code> if none.
   * @throws LlamaException thrown if an error occurred while releasing the
   * placed reservation or expansion.
   */
  public abstract PlacedReservation releaseReservation(UUID handle,
      UUID reservationId, boolean doNotCache)
      throws LlamaException;

  /**
   * Releases all placed reservations associated with the given handle.
   *
   * @param handle the handle of all reservations to release.
   * @param doNotCache indicates if the resources of the placed reservations
   * being released should be cached  (if caching is enabled) or not.
   * @return the list placed reservations being released. The return value
   * is never <code>NULL</code>, if there are not placed reservation for the
   * specified handle, an empty list is returned.f none.
   * @throws LlamaException thrown if an error occurred while releasing the
   * placed reservations for the handle.
   */
  public abstract List<PlacedReservation> releaseReservationsForHandle(
      UUID handle, boolean doNotCache)
      throws LlamaException;

  /**
   * Releases all placed reservations associated with the given queue.
   *
   * @param queue the queue of all reservations to release.
   * @param doNotCache indicates if the resources of the placed reservations
   * being released should be cached  (if caching is enabled) or not.
   * @return the list placed reservations being released. The return value
   * is never <code>NULL</code>, if there are not placed reservation for the
   * specified queue, an empty list is returned.f none.
   * @throws LlamaException thrown if an error occurred while releasing the
   * placed reservations for the queue.
   */
  public abstract List<PlacedReservation> releaseReservationsForQueue(
      String queue, boolean doNotCache) throws LlamaException;

  /**
   * Constant to be used to empty the cache for all queues.
   */
  public static final String ALL_QUEUES = "ALL QUEUES";

  /**
   * Empties the cache for the specified queue.
   *
   * @param queue the queue to empty the cache. To emtpy the cache for all queues
   * the {@link #ALL_QUEUES} constant must be used. IMPORTANT: The constant
   * itself must be used, using a <code>ALL_QUEUES</code> literal won't work.
   * @throws LlamaException thrown if an error occurred while emptying the cache.
   */
  public abstract void emptyCacheForQueue(String queue) throws LlamaException;

  /**
   * Adds a listener to the <code>LlamaAM</code> instance to receive  events.
   *
   * @param listener listener to add.
   */
  public abstract void addListener(LlamaAMListener listener);

  /**
   * Removes a listener from the <code>LlamaAM</code> instance.
   *
   * @param listener listener to remove.
   */
  public abstract void removeListener(LlamaAMListener listener);

  private static final ThreadLocal<Boolean> AS_ADMIN =
      new ThreadLocal<Boolean>();

  /**
   * Tells, a Llama implementation class, if the current call is in the context
   * of an admin operation.
   *
   * @return <code>TRUE</code> if the current call is in teh context of an
   * admin operation, <code>FALSE</code> otherwise.
   * @see #doAsAdmin(Callable)
   */
  protected boolean isAdminCall() {
    return (AS_ADMIN.get() != null) ? AS_ADMIN.get() : false;
  }

  /**
   * Performs a Llama operation as an Admin.
   * <p/>
   * It allows to bypass restrictions imposed by Llama to regular users (such
   * as releasing a reservation without presenting the handle that placed the
   * reservation).
   *
   * @param callable the code to run as an Admin.
   * @param <T> the type returned by the given <code>Callable</code>.
   * @return <T> the value returned by the given <code>Callable</code>.
   * @throws Exception thrown by the {@link Callable#call()} invocation.
   */
  public static <T> T doAsAdmin(Callable<T> callable) throws Exception {
    AS_ADMIN.set(Boolean.TRUE);
    try{
      return callable.call();
    } finally {
      AS_ADMIN.remove();
    }
  }

}
