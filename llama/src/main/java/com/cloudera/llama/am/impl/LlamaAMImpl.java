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
package com.cloudera.llama.am.impl;

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.LlamaAMListener;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public abstract class LlamaAMImpl extends LlamaAM {
  private static final Logger LOG = LoggerFactory.getLogger(LlamaAMImpl.class);
  private final Set<LlamaAMListener> listeners;

  protected LlamaAMImpl(Configuration conf) {
    super(conf);
    listeners = new HashSet<LlamaAMListener>();
  }

  public void addListener(LlamaAMListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  public void removeListener(LlamaAMListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  protected void dispatch(LlamaAMEvent event) {
    if (!event.isEmpty()) {
      synchronized (listeners) {
        for (LlamaAMListener listener : listeners) {
          try {
            listener.onEvent(event);
          } catch (Throwable ex) {
            LOG.warn("listener.handle() error: {}", ex.toString(), ex);
          }
        }
      }
    }
  }

}
