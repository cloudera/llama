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

import org.apache.hadoop.ha.ActiveStandbyElector;
import org.apache.hadoop.ha.ServiceFailedException;
import org.apache.hadoop.net.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

public class LlamaHAServer extends LlamaAMServer
    implements ActiveStandbyElector.ActiveStandbyElectorCallback {
  private static final Logger LOG =
      LoggerFactory.getLogger(LlamaHAServer.class);

  private ActiveStandbyElector elector;

  // Unique identifier for leader election
  private final byte[] localNodeBytes;
  private boolean active = false; // protected by object-wide lock
  LlamaHAFencer fencer;

  public LlamaHAServer() {
    localNodeBytes = (NetUtils.getHostname() + "__" +
        new SecureRandom().nextLong()).getBytes();
  }

  @Override
  public void start() {
    HAServerConfiguration conf = new HAServerConfiguration();
    conf.setConf(getConf());

    if (!conf.isHAEnabled()) {
      transitionToActive();
    } else {
      startHttpServer();

      try {
        elector = new ActiveStandbyElector(conf.getZkQuorum(),
            (int) conf.getZKTimeout(), conf.getElectorZNode(),
            conf.getZkAcls(), conf.getZkAuths(), this);
        elector.ensureParentZNode();

        // Create fencer before joining election but after creating base dirs
        fencer = new LlamaHAFencer(this, conf);

        // Join election
        elector.joinElection(localNodeBytes);
        LOG.info("Join election");
      } catch (Exception e) {
        LOG.error(
            "HA is enabled, but couldn't create leader elector or fencer", e);
        this.shutdown(1);
      }
    }
  }

  @Override
  public void stop() {
    if (elector != null) {
      elector.quitElection(false);
      elector.terminateConnection();
    }
    transitionToStandby();
  }

  @Override
  public void becomeActive() throws ServiceFailedException {
    transitionToActive();
  }

  @Override
  public void becomeStandby() {
    transitionToStandby();
  }

  @Override
  public void enterNeutralMode() {
    // Do nothing
  }

  @Override
  public void notifyFatalError(String s) {
    LOG.error("Shutting down! Fatal error: {}", s);
    shutdown(1);
  }

  @Override
  public void fenceOldActive(byte[] bytes) {
    // We are not using an explicit fencing mechanism.
    // No point trying to fence here.
  }

  void foregoActive(int sleepTime) {
    if (elector != null) {
      elector.quitElection(false);
    }
    transitionToStandby();
    if (sleepTime > 0) {
      try {
        Thread.sleep(sleepTime);
      } catch (InterruptedException e) {
        LOG.error("Interrupted while waiting to rejoin election");
      }
    }
    if (elector != null) {
      elector.joinElection(localNodeBytes);
    }
  }

  private synchronized void transitionToActive() {
    if (!active) {
      if (fencer != null) {
        try {
          fencer.fenceOthers();
        }catch(Exception e){
          LOG.error("Couldn't fence other nodes", e);
          notifyFatalError("Couldn't fence other nodes");
        }
        fencer.startFenceChecker();
      }
      super.start();
      active = true;
    } else {
      LOG.info("Asked to transition to active, when already in active mode.");
    }
  }

  private synchronized void transitionToStandby() {
    if (active) {
      super.stop();
      if (fencer != null) {
        fencer.stopFenceChecker();
      }
      active = false;
    } else {
      LOG.info("Asked to transition to standby, when already in standby mode.");
    }
  }

  public synchronized boolean isActive() {
    return active;
  }
}
