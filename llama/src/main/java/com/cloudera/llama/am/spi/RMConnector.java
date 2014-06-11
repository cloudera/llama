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
package com.cloudera.llama.am.spi;

import com.cloudera.llama.am.api.NodeInfo;
import com.cloudera.llama.util.LlamaException;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.MetricRegistry;

import java.util.Collection;
import java.util.List;

/**
 * The <code>RMConnector</code> is the interaction point between LlamaAM and
 * the underlying Resource Manager. It provides basic resource management upon
 * which it builds all the resource management functionality provided by Llama.
 */
public interface RMConnector {

  /**
   * Sets a {@link RMListener} to receive resource state change events,
   * {@link RMEvent}, from the underlying Resource Manager.
   *
   * @param listener listener to receive resource state change events.
   */
  public void setRMListener(RMListener listener);

  /**
   * Registers the connector with the underlying Resource Manager to work with
   * a particular queue.
   * <p/>
   * A registered connector is not active, it has to be started first.
   * <p/>
   * A register connector cannot make reservations, it can only get the list
   * of nodes from the Resource Manager via the {@link #getNodes()} method.
   *
    * @param queue the Resource Manager queue to work against.
   * @throws LlamaException thrown if the connector could not be registered.
   * @see #start()
   * @see #getNodes()
   */
  public void register(String queue) throws LlamaException;

  /**
   * Un-registers the connector from the underlying Resource Manager.
   * <p/>
   * If the connector is running (started), it should be stopped before
   * un-registering it.
   *
   * @see #stop()
   */
  public void unregister();

  /**
   * Starts the connector. Once the connector is started, it can make
   * reservations.
   *
   * @throws LlamaException thrown if the connector could not be started.
   */
  public void start() throws LlamaException;

  /**
   * Stops the connector.
   * <p/>
   * All existing reservations, regardless of its status are discarded.
   * <p/>
   * Once a connector is stopped, it cannot manage reservations anymore.
   * <p/>
   * A stopped connector cannot be restarted.
   */
  public void stop();

  /**
   * Returns the list of active/usable nodes in the cluster.
   * <p/>
   * The connector must be registered first.
   *
   * @return the list of active/usable nodes in the cluster.
   * @throws LlamaException thrown if there was an error while retrieving the
   * nodes.
   */
  public List<NodeInfo> getNodes() throws LlamaException;

  /**
   * Submits a list of resource requests to the Resource Manager.
   *
   * @param resources the list of resources being requested to the Resource
   * Manager.
   * @throws LlamaException thrown if an error occurred while reserving the
   * resources.
   */
  public void reserve(Collection<RMResource> resources) throws LlamaException;

  /**
   * Releases the given list of resources from the ResourceManager, returning
   * the resources  currently allocated and canceling the outstanding ones.
   *
   * @param resources the list of resources to release.
   * @throws LlamaException thrown if an error occurred while releasing the
   * resources.
   */
  public void release(Collection<RMResource> resources, boolean doNotCache)
      throws LlamaException;

  /**
   * This method allow the code using the connector to reassign an allocated
   * RM resource to a different Llama resource.
   * <p/>
   * This enables a cache to reassign adquired resources when they are parked
   * in the cache and when they are handed out of the cache to a reservation.
   * <p/>
   * Keeping this mapping up to day allows the connector not notify the right
   * Llama reservation of a change in the status of the resource.
   *
   * @param rmResourceId the RM ID for the resource.
   * @param resourceId the new Llama ID to associated to the resource.
   * @return <code>TRUE</code>, if the resource could be reassigned,
   * <code>FALSE</code> otherwise. If a resource cannot be reassigned, it should
   * be discarded.
   */
  public boolean reassignResource(Object rmResourceId, UUID resourceId);

  /**
   * If the <code>RMConnector</code> implementation supports caching and there
   * are cached resources, this method forces releasing all cached resources,
   * returning them to the Resource Manager.
   * <p/>
   * If the connector does not implement caching, this method should do either
   * a no-operation (or, if it corresponds, a pass-through)

   * @throws LlamaException thrown if an error occurred while emptying the
   * cache.
   */
  public void emptyCache() throws LlamaException;

  public void setMetricRegistry(MetricRegistry registry);

  /**
   * Checks if the connector still has pending resources which are yet
   * to be released.
   * @return <code>TRUE</code>, if there are resources reserved,
   * <code>FALSE</code> otherwise.
   */
  public boolean hasResources();

  /**
   * Delete all YARN applications created by this Llama cluster
   */
  public void deleteAllReservations() throws LlamaException;
}
