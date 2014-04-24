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
package com.cloudera.llama.am;

import com.cloudera.llama.util.ErrorCode;
import com.cloudera.llama.util.LlamaException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.hadoop.util.ZKUtil;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.auth.DigestAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Fencer to make sure a single Llama is Active.
 *
 * Fencing is achieved through Zookeeper ACLs on a particular znode
 * (say, fencingZNode). When transitioning to Active,
 * a Llama claims exclusive read-access to this znode and fences other Llamas
 * falsely assuming Active role. Also, each Active Llama runs a thread that
 * tries to read this znode in a loop; when another Llama takes over,
 * this read fails and the monitoring Llama gives up the Active role.
 */
public class LlamaHAFencer {
  private static final Logger LOG =
      LoggerFactory.getLogger(LlamaHAFencer.class);
  private static final int MAX_RETRIES = 3;

  private LlamaHAServer server;
  private HAServerConfiguration conf;

  // Fence checking variables
  private FenceChecker checker;
  private long checkingInterval;

  // ZK related variables
  private final String zkQuorum;
  final int zkSessionTimeout;
  private List<ZKUtil.ZKAuthInfo> zkAuths;
  private List<ACL> exclusiveReadAccessACLs;
  private final String authScheme =
      new DigestAuthenticationProvider().getScheme();
  private final String fencingUsername;
  private final String fencingPassword;
  private final String fencingPath;

  // Curator-specific
  private CuratorFramework client;
  private long zkClientIndex = -1;

  LlamaHAFencer(LlamaHAServer server, HAServerConfiguration conf)
      throws Exception {
    this.server = server;
    this.conf = conf;
    this.checkingInterval = conf.getZKTimeout() / 5;

    // ZK related initialization
    zkQuorum = conf.getZkQuorum();
    zkSessionTimeout = (int) conf.getZKTimeout();

    fencingPath = conf.getFencingZNode();
    SecureRandom random = new SecureRandom();
    fencingUsername = Long.toString(random.nextLong());
    fencingPassword = Long.toString(random.nextLong());

    // Create Curator client using the auths for the exclusive access,
    // the shared access auths are added after the fact.
    client = CuratorFrameworkFactory.builder()
        .connectString(zkQuorum)
        .connectionTimeoutMs(zkSessionTimeout)
        .retryPolicy(new ExponentialBackoffRetry(1000, MAX_RETRIES))
        .authorization(authScheme,
            (fencingUsername + ":" + fencingPassword).getBytes())
        .build();
    client.start();

    // Update zkAuths of the underlying ZK Client to include the ones for
    // shared c-d-w-a access.
    updateZKAuthsIfRequired();

    // Create fencing-path if it doesn't exist
    try {
      client.create()
          .creatingParentsIfNeeded()
          .withMode(CreateMode.PERSISTENT)
          .withACL(conf.getZkAcls())
          .forPath(fencingPath, "dummy-data".getBytes());
    } catch (KeeperException.NodeExistsException nee) {
      // Ignore.
    }

    // Setup for exclusive access
    exclusiveReadAccessACLs = createAclsForExclusiveReadAccess();
    LOG.info("Configured exclusive read-access ACLs - {}",
        exclusiveReadAccessACLs);

    LOG.info("Created fencer.");
  }

  public void startFenceChecker() {
    checker = new FenceChecker();
    checker.start();
    LOG.info("Started Fence Checker.");
  }

  public void stopFenceChecker() {
    if (checker != null) {
      checker.interrupt();
      // checker.join() is not required. Worst case,
      // it will stop when it gets fenced.
      checker = null;
      LOG.info("Stopped Fence Checker.");
    }
  }

  public void fenceOthers() throws Exception {
    LOG.info("Fencing any other Llamas assuming Active role...");
    updateZKAuthsIfRequired();
    client.setACL().withACL(exclusiveReadAccessACLs).forPath(fencingPath);
    LOG.info("Done fencing other Llamas.");
  }

  /**
   * Helper method to add the configured (shared) ZKAuths in case Curator's
   * underlying ZK Client is updated. This needs to be called before creating
   * the {@link #fencingPath} and in the call to {@link #fenceOthers}.
   */
  private void updateZKAuthsIfRequired() throws Exception {
    long latestZkClientIndex = client.getZookeeperClient().getInstanceIndex();
    assert (latestZkClientIndex >= zkClientIndex);

    while (latestZkClientIndex > zkClientIndex) {
      zkClientIndex = latestZkClientIndex;
      ZooKeeper zkClient = client.getZookeeperClient().getZooKeeper();
      for (ZKUtil.ZKAuthInfo zkAuth : conf.getZkAuths()) {
        zkClient.addAuthInfo(zkAuth.getScheme(), zkAuth.getAuth());
      }
    }
  }

  private List<ACL> createAclsForExclusiveReadAccess() throws LlamaException {
    List<ACL> acls = new ArrayList<ACL>();
    for (ACL acl : conf.getZkAcls()) {
      acls.add(new ACL(
          ZKUtil.removeSpecificPerms(acl.getPerms(), ZooDefs.Perms.READ),
          acl.getId()));
    }
    Id llamaId;
    try {
      llamaId =
          new Id(authScheme, DigestAuthenticationProvider.generateDigest(
              fencingUsername + ":" + fencingPassword));
    } catch (NoSuchAlgorithmException e) {
      throw new LlamaException(ErrorCode.INTERNAL_ERROR,
          "Unable to create username:password digest for ZK");
    }
    acls.add(new ACL(ZooDefs.Perms.READ, llamaId));
    return acls;
  }

  private class FenceChecker extends Thread {
    public void run() {
      // Keep checking if this instance got fenced
      while (true) {
        try {
          client.getData().forPath(fencingPath);
          Thread.sleep(checkingInterval);
        } catch (InterruptedException e) {
          LOG.error("Interrupted!", e);
          break;
        } catch (Exception e) {
          LOG.error("Potentially fenced!", e);
          break;
        }
      }

      // Fenced or interrupted. Forego active status immediately.
      server.foregoActive(0);
    }
  }
}
