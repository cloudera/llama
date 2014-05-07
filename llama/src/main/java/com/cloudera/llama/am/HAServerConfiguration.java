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
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ZKUtil;
import org.apache.zookeeper.data.ACL;

import java.util.Collections;
import java.util.List;

public class HAServerConfiguration implements Configurable {
  private Configuration conf;
  public static final String KEY_PREFIX = "llama.am.ha.";

  @Override
  public void setConf(Configuration entries) {
    this.conf = entries;
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  /** Enabled HA */
  public static final String HA_ENABLED = KEY_PREFIX + "enabled";

  public boolean isHAEnabled() {
    return conf.getBoolean(HA_ENABLED, false);
  }

  /** ZK configs */
  public static final String ZK_PREFIX = KEY_PREFIX + "zk-";

  /** Base directory */
  public static final String ZK_BASE = ZK_PREFIX + "base";
  public static final String ZK_BASE_DEFAULT = "/llama";
  private String zkBase;

  private String getZkBase() throws LlamaException {
    if (zkBase == null) {
      zkBase = conf.get(ZK_BASE, ZK_BASE_DEFAULT);
    }
    if (!zkBase.startsWith("/")) {
      throw new LlamaException(ErrorCode.ILLEGAL_ARGUMENT, ZK_BASE,
          " should start with '/'");
    }
    return zkBase;
  }

  public String getElectorZNode() throws LlamaException {
    return getZkBase() + "/leader-election";
  }

  public String getFencingZNode() throws LlamaException {
    return getZkBase() + "/fencing";
  }

  /** ZK quorum */
  public static final String ZK_QUORUM = ZK_PREFIX + "quorum";

  public String getZkQuorum() throws LlamaException {
    String zkQuorum = conf.get(ZK_QUORUM);
    if (zkQuorum == null || zkQuorum.equals("")) {
      throw new LlamaException(ErrorCode.ILLEGAL_ARGUMENT, ZK_QUORUM,
          " needs to be set when ", HA_ENABLED, " is set.");
    }
    return zkQuorum;
  }

  /** More ZK confs */
  public static final String ZK_TIMEOUT_MS = ZK_PREFIX + "timeout-ms";
  public static final int ZK_TIMEOUT_MS_DEFAULT = 10 * 1000;

  public long getZKTimeout() {
    return conf.getLong(ZK_TIMEOUT_MS, ZK_TIMEOUT_MS_DEFAULT);
  }

  public static final String ZK_ACL = ZK_PREFIX + "acl";
  public static final String ZK_ACL_DEFAULT = "world:anyone:rwcda";

  public List<ACL> getZkAcls() throws LlamaException {
    // Parse authentication from configuration.
    String zkAclConf = conf.get(ZK_ACL, ZK_ACL_DEFAULT);
    try {
      zkAclConf = ZKUtil.resolveConfIndirection(zkAclConf);
      return ZKUtil.parseACLs(zkAclConf);
    } catch (Exception e) {
      throw new LlamaException(e, ErrorCode.ILLEGAL_ARGUMENT,
          "Couldn't read ACLs based on ", ZK_ACL);
    }
  }

  public static final String ZK_AUTH = ZK_PREFIX + "auth";

  public List<ZKUtil.ZKAuthInfo> getZkAuths() throws LlamaException {
    String zkAuthConf = conf.get(ZK_AUTH);
    try {
      zkAuthConf = ZKUtil.resolveConfIndirection(zkAuthConf);
      if (zkAuthConf != null) {
        return ZKUtil.parseAuth(zkAuthConf);
      } else {
        return Collections.emptyList();
      }
    } catch (Exception e) {
      throw new LlamaException(e, ErrorCode.ILLEGAL_ARGUMENT,
          "Couldn't read Auth based on ", ZK_AUTH);
    }
  }
}
