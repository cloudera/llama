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
  private final String randomComponent =
      Long.toString(new SecureRandom().nextLong());

  private ActiveStandbyElector elector;
  private byte[] localNodeBytes;
  private boolean active = false;

  public LlamaHAServer() {}

  @Override
  public void start() {
    HAServerConfiguration conf = new HAServerConfiguration();
    conf.setConf(getConf());

    if (!conf.isHAEnabled()) {
      transitionToActive();
    } else {
      fillLocalNodeBytes();
      try {
        elector = new ActiveStandbyElector(conf.getZkQuorum(),
            (int) conf.getZKTimeout(), conf.getElectorZNode(),
            conf.getZkAcls(), conf.getZkAuths(), this);
        elector.ensureParentZNode();
        elector.joinElection(localNodeBytes);
        LOG.info("Join election");
      } catch (Exception e) {
        LOG.error("HA is enabled, but couldn't create leader elector", e);
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
    this.shutdown(1);
  }

  @Override
  public void fenceOldActive(byte[] bytes) {
    // TODO: Fence the old active
  }

  /** Helper method for tests */
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
      super.start();
      active = true;
    }
  }

  private synchronized void transitionToStandby() {
    if (active) {
      super.stop();
      active = false;
    }
  }

  public synchronized boolean isActive() {
    return active;
  }

  private void fillLocalNodeBytes() {
    localNodeBytes =
        (NetUtils.getHostname() + "__" + randomComponent).getBytes();
  }
}
