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
package com.cloudera.llama.am.yarn;

import com.cloudera.llama.am.LlamaAM;
import com.cloudera.llama.am.LlamaAMException;
import com.cloudera.llama.am.PlacedResource;
import com.cloudera.llama.am.impl.FastFormat;
import com.cloudera.llama.am.spi.RMLlamaAMAdapter;
import com.cloudera.llama.am.spi.RMLlamaAMCallback;
import com.cloudera.llama.am.spi.RMPlacedReservation;
import com.cloudera.llama.am.spi.RMPlacedResource;
import com.cloudera.llama.am.spi.RMResourceChange;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;

import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.client.api.NMClient;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.Records;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class YarnRMLlamaAMAdapter implements RMLlamaAMAdapter, Configurable, 
    AMRMClientAsync.CallbackHandler {
  private static final Logger LOG = LoggerFactory.getLogger(YarnRMLlamaAMAdapter.class);
  
  public static final String PREFIX_KEY = LlamaAM.PREFIX_KEY + "yarn.";

  private static final String AM_PRIORITY_KEY = PREFIX_KEY + "priority";
  private static final int AM_PRIORITY_DEFAULT = 0;

  private static final String APP_MONITOR_TIMEOUT_KEY = PREFIX_KEY + 
      "app.monitor.timeout.ms";  
  private static final long APP_MONITOR_TIMEOUT_DEFAULT = 30000;

  private static final String APP_MONITOR_POLLING_KEY = PREFIX_KEY + 
      "app.monitor.polling.ms";
  private static final long APP_MONITOR_POLLING_DEFAULT = 200;

  private static final String HEARTBEAT_INTERVAL_KEY = PREFIX_KEY + 
      "app.heartbeat.interval.ms";
  private static final int HEARTBEAT_INTERNAL_DEFAULT = 200;

  public static final String CONTAINER_HANDLER_QUEUE_THRESHOLD_KEY = PREFIX_KEY
      + "container.handler.queue.threshold";
  public static final int CONTAINER_HANDLER_QUEUE_THRESHOLD_DEFAULT = 10000;

  public static final String CONTAINER_HANDLER_THREADS_KEY = PREFIX_KEY + 
      "container.handler.threads";
  public static final int CONTAINER_HANDLER_THREADS_DEFAULT = 10;

  public static final String HADOOP_USER_NAME_KEY = PREFIX_KEY + 
        "hadoop.user.name";
  public static final String HADOOP_USER_NAME_DEFAULT = "llama";

  private Configuration conf;
  private RMLlamaAMCallback llamaCallback;
  private UserGroupInformation ugi;
  private YarnClient rmClient;
  private AMRMClientAsync<LlamaContainerRequest> amClient;
  private NMClient nmClient;
  private ApplicationId appId;
  private final Set<String> nodes;
  private int maxMem;
  private int maxCPUs;
  private int containerHandlerQueueThreshold;
  private BlockingQueue<ContainerHandler> containerHandlerQueue;
  private ThreadPoolExecutor containerHandlerExecutor;

  public YarnRMLlamaAMAdapter() {
    nodes = Collections.synchronizedSet(new HashSet<String>());
  }

  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  @Override
  public void setLlamaAMCallback(RMLlamaAMCallback callback) {
    llamaCallback = callback;
  }

  private UserGroupInformation createUGIForApp() throws Exception {
    String userName = getConf().get(HADOOP_USER_NAME_KEY, 
        HADOOP_USER_NAME_DEFAULT);
    UserGroupInformation llamaUGI = UserGroupInformation.getLoginUser();
    return UserGroupInformation.createProxyUser(userName, llamaUGI);
  }
    
  @Override
  @SuppressWarnings("unchecked")
  public void register(final String queue) throws LlamaAMException {
    try {
      ugi = createUGIForApp();
      ugi.doAs(new PrivilegedExceptionAction<Void>() {
        @Override
        public Void run() throws Exception {
          _initYarnApp(queue);
          return null;
        }
      });
      containerHandlerQueueThreshold = getConf().getInt(
          CONTAINER_HANDLER_QUEUE_THRESHOLD_KEY,
          CONTAINER_HANDLER_QUEUE_THRESHOLD_DEFAULT);
      containerHandlerQueue = new LinkedBlockingQueue<ContainerHandler>();
      int threads = getConf().getInt(CONTAINER_HANDLER_THREADS_KEY, 
          CONTAINER_HANDLER_THREADS_DEFAULT);
      //funny downcasting and upcasting because javac gets goofy here
      containerHandlerExecutor = new ThreadPoolExecutor(threads, threads, 0, 
          TimeUnit.SECONDS, (BlockingQueue<Runnable>) (BlockingQueue) 
          containerHandlerQueue);
      containerHandlerExecutor.prestartAllCoreThreads();
    } catch (Exception ex) {
      throw new LlamaAMException(ex);
    }
  }

  private void _initYarnApp(String queue) throws Exception {
    Configuration yarnConf = new YarnConfiguration();
    for (Map.Entry entry : getConf()) {
      yarnConf.set((String) entry.getKey(), (String) entry.getValue());
    }
    rmClient = YarnClient.createYarnClient();
    rmClient.init(yarnConf);
    rmClient.start();
    nmClient = NMClient.createNMClient();
    nmClient.init(yarnConf);
    nmClient.start();
    appId = _createApp(rmClient, queue);
    _monitorAppState(rmClient, appId, ACCEPTED);
    ugi.addToken(rmClient.getAMRMToken(appId));
    int heartbeatInterval = getConf().getInt(HEARTBEAT_INTERVAL_KEY, 
        HEARTBEAT_INTERNAL_DEFAULT);
    amClient = AMRMClientAsync.createAMRMClientAsync(heartbeatInterval, 
        YarnRMLlamaAMAdapter.this);
    amClient.init(yarnConf);
    amClient.start();
    RegisterApplicationMasterResponse response = amClient
        .registerApplicationMaster("", 0, "");
    maxMem = response.getMaximumResourceCapability().getMemory();
    maxCPUs = response.getMaximumResourceCapability().getVirtualCores();
    for (NodeReport nodeReport : rmClient.getNodeReports()) {
      if (nodeReport.getNodeState() == NodeState.RUNNING) {
        nodes.add(nodeReport.getNodeId().getHost());
      }
    }
  }

  private ApplicationId _createApp(YarnClient rmClient, String queue)  
      throws LlamaAMException {
    try {
      // Create application
      YarnClientApplication newApp = rmClient.createApplication();
      ApplicationId appId = newApp.getNewApplicationResponse().
          getApplicationId();

      // Create launch context for app master
      ApplicationSubmissionContext appContext = Records.newRecord(
          ApplicationSubmissionContext.class);

      // set the application id
      appContext.setApplicationId(appId);

      // set the application name
      appContext.setApplicationName("LlamaAM");

      // Set the priority for the application master
      Priority pri = Records.newRecord(Priority.class);
      int priority = getConf().getInt(AM_PRIORITY_KEY, AM_PRIORITY_DEFAULT);
      pri.setPriority(priority);
      appContext.setPriority(pri);

      // Set the queue to which this application is to be submitted in the RM
      appContext.setQueue(queue);

      // Set up the container launch context for the application master
      ContainerLaunchContext amContainer = Records.newRecord(
          ContainerLaunchContext.class);
      appContext.setAMContainerSpec(amContainer);

      // unmanaged AM
      appContext.setUnmanagedAM(true);

      // Submit the application to the applications manager
      return  rmClient.submitApplication(appContext);
    } catch (Exception ex) {
      throw new LlamaAMException(ex);
    }
  }

  private static final Set<YarnApplicationState> ACCEPTED = EnumSet.of
      (YarnApplicationState.ACCEPTED);

  private static final Set<YarnApplicationState> STOPPED = EnumSet.of(
      YarnApplicationState.KILLED, YarnApplicationState.FAILED,
      YarnApplicationState.FINISHED);

  private ApplicationReport _monitorAppState(YarnClient rmClient,
      ApplicationId appId, Set<YarnApplicationState> states)
      throws LlamaAMException {
    try {
      long timeout = getConf().getLong(APP_MONITOR_TIMEOUT_KEY, 
          APP_MONITOR_TIMEOUT_DEFAULT);

      long polling = getConf().getLong(APP_MONITOR_POLLING_KEY, 
          APP_MONITOR_POLLING_DEFAULT);

      long start = System.currentTimeMillis();
      ApplicationReport report = rmClient.getApplicationReport(appId);
      while (!states.contains(report.getYarnApplicationState())) {
        if (System.currentTimeMillis() - start > timeout) {
          throw new LlamaAMException(FastFormat.format(
              "App '{}' time out, failed to reach states '{}'", appId, states));
        }
        Thread.sleep(polling);
        report = rmClient.getApplicationReport(appId);
      }
      return report;
    } catch (Exception ex) {
      _stop(FinalApplicationStatus.FAILED, "Could not start, error: " + ex);
      throw new LlamaAMException(ex);
    }
  }

  @Override
  public void unregister() {
    ugi.doAs(new PrivilegedAction<Void>() {
      @Override
      public Void run() {
        _stop(FinalApplicationStatus.SUCCEEDED, "Stopped by AM");
        return null;
      }
    });
  }
  
  private synchronized void _stop(FinalApplicationStatus status, String msg) {
    if (containerHandlerExecutor != null) {
      containerHandlerExecutor.shutdownNow();
      containerHandlerExecutor = null;
    }
    if (amClient != null) {
      try {
        amClient.unregisterApplicationMaster(status, msg, "");
      } catch (Exception ex) {
        LOG.warn("Error un-registering AM client, " + ex, ex);
      }
      amClient.stop();
      amClient = null;
    }
    if (rmClient != null) {
      try {
        ApplicationReport report = _monitorAppState(rmClient, appId, STOPPED);
        if (report.getFinalApplicationStatus()
            != FinalApplicationStatus.SUCCEEDED) {
          LOG.warn("Problem stopping application, final status '{}'",
              report.getFinalApplicationStatus());
        }
      } catch (Exception ex) {
        LOG.warn("Error stopping application, " + ex, ex);
      }
      rmClient.stop();
      rmClient = null;
    }
    if (nmClient != null) {
      //TODO this is introducing a deadlock
      //nmClient.stop();
    }
  }

  @Override  
  public List<String> getNodes() throws LlamaAMException {
    synchronized (nodes) {
      return new ArrayList<String>(nodes);
    }
  }

  static class LlamaContainerRequest extends AMRMClient.ContainerRequest {
    private static final Priority PRIORITY = Priority.newInstance(1);
    private static final String[] RACKS = new String[0];
    
    public static Resource createResource(PlacedResource placedResource) {
      return Resource.newInstance(placedResource.getMemoryMb(), 
          placedResource.getCpuVCores());
    }
    
    private RMPlacedResource placedResource;
    
    public LlamaContainerRequest(RMPlacedResource placedResource) {
      super(createResource(placedResource), 
          new String[]{placedResource.getLocation()}, RACKS, PRIORITY,
          (placedResource.getEnforcement() != com.cloudera.llama.am.Resource
              .LocationEnforcement.MUST));
      this.placedResource = placedResource;
    }
    
    public RMPlacedResource getPlacedResource() {
      return placedResource;
    }
  }
  
  private void _reserve(RMPlacedReservation reservation) 
      throws LlamaAMException {
    for (PlacedResource resource : reservation.getResources()) {
      if (!nodes.contains(resource.getLocation())) {
        throw new LlamaAMException(FastFormat.format(
            "Node '{}' is not available", resource.getLocation()));
      }
      if (resource.getMemoryMb() > maxMem) {
        throw new LlamaAMException(FastFormat.format(
            "Resource '{}' asking for '{}' memory exceeds maximum '{}'",
            resource.getClientResourceId(), resource.getMemoryMb(), maxMem));        
      }
      if (resource.getCpuVCores() > maxCPUs) {
        throw new LlamaAMException(FastFormat.format(
        "Resource '{}' asking for '{}' CPUs exceeds maximum '{}'",
            resource.getClientResourceId(), resource.getCpuVCores(), maxCPUs));
      }
    }
    for (RMPlacedResource resource : reservation.getRMResources()) {
      LlamaContainerRequest request = new LlamaContainerRequest(resource);
      amClient.addContainerRequest(request);
      resource.setRmPayload(request);
    }    
  }

  @Override
  public void reserve(final RMPlacedReservation reservation) 
      throws LlamaAMException {
    try {
      ugi.doAs(new PrivilegedExceptionAction<Void>() {
        @Override
        public Void run() throws Exception {
          _reserve(reservation);
          return null;
        }
      });
    } catch (Throwable ex) {
      if (ex instanceof LlamaAMException) {
        throw (LlamaAMException) ex;
      } else {
        throw new RuntimeException(ex);
      }
    }
  }

  private void _release(Collection<RMPlacedResource> resources)
      throws LlamaAMException {
    for (RMPlacedResource resource : resources) {
      Object payload = resource.getRmPayload();
      if (payload != null) {
        if (payload instanceof LlamaContainerRequest) {
          amClient.removeContainerRequest((LlamaContainerRequest) payload);          
        } else if (payload instanceof Container) {
          Container container = ((Container) payload);
          containerIdToClientResourceIdMap.remove(container.getId());
          queue(new ContainerHandler(ugi, resource.getClientResourceId(), 
              container, Action.STOP));
        }
      }    
    }
  }
    
  @Override
  public void release(final Collection<RMPlacedResource> resources)
      throws LlamaAMException {
    try {
      ugi.doAs(new PrivilegedExceptionAction<Void>() {
        @Override
        public Void run() throws Exception {
          _release(resources);
          return null;
        }
      });
    } catch (Throwable ex) {
      if (ex instanceof LlamaAMException) {
        throw (LlamaAMException) ex;
      } else {
        throw new RuntimeException(ex);
      }
    }
  }

  // YARN AMMRClientAsync#CallbackHandler methods

  ConcurrentHashMap<ContainerId, UUID> containerIdToClientResourceIdMap =
      new ConcurrentHashMap<ContainerId, UUID>();

  @Override
  public void onContainersCompleted(List<ContainerStatus> containerStatuses) {
    List<RMResourceChange> changes = new ArrayList<RMResourceChange>();
    for (ContainerStatus containerStatus : containerStatuses) {
      ContainerId containerId = containerStatus.getContainerId();
      UUID clientResourceId = containerIdToClientResourceIdMap.
          remove(containerId);      
      if (clientResourceId != null) {
        //TODO this is not correct, we cannot differentiate between preempted 
        // and lost, YARN-1049
        changes.add(RMResourceChange.createResourceChange(clientResourceId,
            PlacedResource.Status.PREEMPTED));
      }
    }
    llamaCallback.changesFromRM(changes);
  }

  private enum Action { START, STOP }
  
  class ContainerHandler implements Runnable {
    final private UserGroupInformation ugi;
    final private UUID clientResourceId;
    final private Container container;
    final private Action action;
    
    public ContainerHandler(UserGroupInformation ugi, UUID clientResourceId, 
        Container container, Action action) {
      this.ugi = ugi;
      this.clientResourceId = clientResourceId;
      this.container = container;
      this.action = action;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        ugi.doAs(new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() throws Exception {
            if (action == Action.START) {
              ContainerLaunchContext ctx = 
                  Records.newRecord(ContainerLaunchContext.class);
              ctx.setEnvironment(Collections.EMPTY_MAP);
              ctx.setLocalResources(Collections.EMPTY_MAP);
              ctx.setCommands(Arrays.asList("sleep", "315360000")); //10 years
              nmClient.startContainer(container, ctx);
            } else {
              nmClient.stopContainer(container.getId(), container.getNodeId());          
            }
            return null;
          }
        });
      } catch (Exception ex) {
        LOG.warn(
            "Could not {} container '{}' for resource '{}' at node '{}', {}'",
            action, container.getId(), clientResourceId, 
            container.getNodeId().getHost(), ex.toString(), ex);
        if (action == Action.START) {
          List<RMResourceChange> changes = new ArrayList<RMResourceChange>();
          changes.add(RMResourceChange.createResourceChange(clientResourceId,
              PlacedResource.Status.LOST));
          llamaCallback.changesFromRM(changes);
        }
      }
    }
  }
  
  private void queue(ContainerHandler handler) {
    containerHandlerQueue.add(handler);
    int size = containerHandlerQueue.size();
    if (size > containerHandlerQueueThreshold) {
      LOG.warn("Container handler queue over '{}' threshold at '{}'",
          containerHandlerQueueThreshold, size);
    }
  }

  @Override
  public void onContainersAllocated(List<Container> containers) {
    List<RMResourceChange> changes = new ArrayList<RMResourceChange>();
    // no need to use a ugi.doAs() as this is called from within Yarn client
    for (Container container : containers) {
      List<? extends Collection<LlamaContainerRequest>> list = 
          amClient.getMatchingRequests(container.getPriority(),
          container.getNodeId().getHost(), container.getResource());
      if (!list.isEmpty()) {
        Collection<LlamaContainerRequest> coll = list.get(0);
        LlamaContainerRequest req = coll.iterator().next();
        RMPlacedResource pr = req.getPlacedResource();
        pr.setRmPayload(container);
        containerIdToClientResourceIdMap.put(container.getId(), 
            pr.getClientResourceId());
        changes.add(RMResourceChange.createResourceAllocation(pr
            .getClientResourceId(), container.getId().toString(), 
            container.getResource().getVirtualCores(), 
            container.getResource().getMemory(), container.getNodeId()
            .getHost()));
        amClient.removeContainerRequest(req);
        queue(new ContainerHandler(ugi, pr.getClientResourceId(), container, 
            Action.START));
      }
    }
    llamaCallback.changesFromRM(changes);
  }

  @Override
  public void onShutdownRequest() {
    llamaCallback.loseAllReservations();
    llamaCallback.setRunning(false);
    // no need to use a ugi.doAs() as this is called from within Yarn client
    _stop(FinalApplicationStatus.FAILED, "Shutdown by Yarn");
  }

  @Override
  public void onNodesUpdated(List<NodeReport> nodeReports) {
    for (NodeReport node : nodeReports) {
      String host = node.getNodeId().getHost();
      if (node.getNodeState() ==  NodeState.RUNNING) {
        nodes.add(host);
      } else {
        nodes.remove(host);        
      }
    }
  }

  @Override
  public float getProgress() {
    return 0;
  }

  @Override
  public void onError(final Throwable ex) {
    LOG.error("Error in Yarn client: {}", ex.toString(), ex);
    llamaCallback.setRunning(false);
    llamaCallback.loseAllReservations();
    // no need to use a ugi.doAs() as this is called from within Yarn client
    _stop(FinalApplicationStatus.FAILED, "Error in Yarn client: " + ex
        .toString());
  }

}
