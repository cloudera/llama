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
package com.cloudera.llama.server;

import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.impl.FastFormat;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientNotificationService implements ClientNotifier.ClientRegistry,
    LlamaAMListener {

  public interface UnregisterListener {

    public void onUnregister(UUID handle);

  }

  private class Entry {
    private final String clientId;
    private final String host;
    private final int port;
    private final ClientCaller caller;

    public Entry(String clientId, UUID handle, String host, int port) {
      this.clientId = clientId;
      this.host = host;
      this.port = port;
      caller = new ClientCaller(conf, clientId, handle, host, port);
    }
  }

  private final ServerConfiguration conf;
  private final ClientNotifier clientNotifier;
  private final ReentrantReadWriteLock lock;
  //MAP of handle to client Entry
  private final ConcurrentHashMap<UUID, Entry> clients;
  //Map of clientId to handle (for reverse lookup)
  private final ConcurrentHashMap<String, UUID> clientIdToHandle;
  //Map of callback-address to handle (for reverse lookup)
  private final ConcurrentHashMap<String, UUID> callbackToHandle;
  private final UnregisterListener unregisterListener;

  public ClientNotificationService(ServerConfiguration conf) {
    this(conf, null, null);
  }

  public ClientNotificationService(ServerConfiguration conf,
      NodeMapper nodeMapper, UnregisterListener unregisterListener) {
    this.conf = conf;
    lock = new ReentrantReadWriteLock();
    clients = new ConcurrentHashMap<UUID, Entry>();
    clientIdToHandle = new ConcurrentHashMap<String, UUID>();
    callbackToHandle = new ConcurrentHashMap<String, UUID>();
    clientNotifier = new ClientNotifier(conf, nodeMapper, this);
    this.unregisterListener = unregisterListener;
  }

  public void start() throws Exception {
    clientNotifier.start();
  }

  public void stop() {
    clientNotifier.stop();
  }

  private String getAddress(String host, int port) {
    return host.toLowerCase() + ":" + port;
  }
  private UUID registerNewClient(String clientId, String host, int port) {
    UUID handle = UUID.randomUUID();
    clients.put(handle, new Entry(clientId, handle, host, port));
    clientIdToHandle.put(clientId, handle);
    callbackToHandle.put(getAddress(host, port), handle);
    clientNotifier.registerClientForHeartbeats(handle);
    return handle;
  }


  public synchronized UUID register(String clientId, String host, int port)
      throws ClientRegistryException {
    lock.writeLock().lock();
    try {
      UUID handle;
      UUID clientIdHandle = clientIdToHandle.get(clientId);
      UUID callbackHandle = callbackToHandle.get(getAddress(host, port));
      if (clientIdHandle == null && callbackHandle == null) {
        //NEW HANDLE
        handle = registerNewClient(clientId, host, port);
      } else if (clientIdHandle == null && callbackHandle != null) {
        //NEW HANDLE, delete reservations from old handle
        unregister(callbackHandle);
        handle = registerNewClient(clientId, host, port);
      } else if (clientIdHandle != null && callbackHandle == null) {
        //ERROR
        Entry entry = clients.get(clientIdHandle);
        throw new ClientRegistryException(FastFormat.format("ClientId '{}' " +
            "already registered with a different notification address {}",
            clientId, getAddress(entry.host, entry.port)));
      } else if (clientIdHandle == callbackHandle) {
        handle = clientIdHandle;
      } else {
        //ERROR
        throw new ClientRegistryException(FastFormat.format("ClientId '{}' " +
            "and notification address '{}' already used in two different " +
            "active registrations '{}' and '{}'", clientId,
            getAddress(host, port), clientIdHandle, callbackHandle));
      }
      return handle;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public synchronized boolean unregister(UUID handle) {
    boolean ret = false;
    lock.writeLock().lock();
    try {
      Entry entry = clients.remove(handle);
      if (entry != null) {
        entry.caller.cleanUpClient();
        clientIdToHandle.remove(entry.clientId);
        callbackToHandle.remove(getAddress(entry.host, entry.port));
        ret = true;
      }
    } finally {
      lock.writeLock().unlock();
    }
    if (ret & unregisterListener != null) {
      unregisterListener.onUnregister(handle);
    }
    return ret;
  }

  public void validateHandle(UUID handle) throws ClientRegistryException {
    lock.readLock().lock();
    try {
      if (!clients.containsKey(handle)) {
        throw new ClientRegistryException(FastFormat.format(
            "Unknown handle '{}' ", handle));
      }
    } finally {
      lock.readLock().unlock();
    }
  }

  public ClientCaller getClientCaller(UUID handle) {
    lock.readLock().lock();
    try {
      Entry entry = clients.get(handle);
      return (entry != null) ? entry.caller : null;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public void onMaxFailures(UUID handle) {
    unregister(handle);
  }

  @Override
  public void handle(LlamaAMEvent event) {
    clientNotifier.handle(event);
  }

}
