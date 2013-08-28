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
package com.cloudera.llama.am.server.thrift;

import com.cloudera.llama.am.LlamaAMEvent;
import com.cloudera.llama.am.LlamaAMListener;
import com.cloudera.llama.thrift.TLlamaAMNotificationRequest;
import com.cloudera.llama.thrift.TLlamaAMNotificationResponse;
import org.apache.hadoop.conf.Configuration;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ClientNotifier implements LlamaAMListener {
  private static final Logger LOG = LoggerFactory.getLogger(
      ClientNotifier.class);

  public interface MaxFailuresListener {
    void onMaxFailures(UUID handle);
  }

  private final Configuration conf;
  private final ClientNotificationService clientNotificationService;
  private final NodeMapper nodeMapper;
  private int queueThreshold;
  private int maxRetries;
  private int retryInverval;
  private DelayQueue<Notifier> eventsQueue;
  private ThreadPoolExecutor executor;
  private Subject subject;
  
  public ClientNotifier(Configuration conf, NodeMapper nodeMapper, 
      ClientNotificationService clientNotificationService) {
    this.conf = conf;
    this.nodeMapper = nodeMapper;
    queueThreshold = conf.getInt(
        ServerConfiguration.CLIENT_NOTIFIER_QUEUE_THRESHOLD_KEY,
        ServerConfiguration.CLIENT_NOTIFIER_QUEUE_THRESHOLD_DEFAULT);
    maxRetries = conf.getInt(
        ServerConfiguration.CLIENT_NOTIFIER_MAX_RETRIES_KEY,
        ServerConfiguration.CLIENT_NOTIFIER_MAX_RETRIES_DEFAULT);
    retryInverval = conf.getInt(
        ServerConfiguration.CLIENT_NOTIFIER_RETRY_INTERVAL_KEY,
        ServerConfiguration.CLIENT_NOTIFIER_RETRY_INTERVAL_DEFAULT);
    this.clientNotificationService = clientNotificationService;
  }

  @SuppressWarnings("unchecked")
  public void start() throws Exception {
    eventsQueue = new DelayQueue<Notifier>();
    int threads = conf.getInt(
        ServerConfiguration.CLIENT_NOTIFIER_THREADS_KEY,
        ServerConfiguration.CLIENT_NOTIFER_THREADS_DEFAULT);
    //funny downcasting and upcasting because javac gets goofy here
    executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS,
        (BlockingQueue<Runnable>) (BlockingQueue) eventsQueue);
    executor.prestartAllCoreThreads();
    subject = Security.loginClientSubject(conf);
  }
  
  public void stop() {
    executor.shutdownNow();
    Security.logout(subject);
  }
  
  @Override
  public void handle(LlamaAMEvent event) {
    if (!event.isEmpty()) {
      eventsQueue.add(new Notifier(event));
      int size = eventsQueue.size();
      if (size > queueThreshold) {
        LOG.warn("Outbound events queue over '{}' threshold at '{}",
            queueThreshold, size);
      }
    }
  }

  public class Notifier implements Runnable, Delayed {
    private LlamaAMEvent event;
    private int retries;
    private int relativeDelay;
    private long absoluteDelay;

    public Notifier(LlamaAMEvent event) {
      this.event = event;
      retries = 0;
      relativeDelay = 0;
      absoluteDelay = System.currentTimeMillis();
    }

    @Override
    public void run() {
      UUID handle = event.getClientId();
      String clientId = null;
      try {
        final ClientCaller clientCaller = 
            clientNotificationService.getClientCaller(handle);
        if (clientCaller != null) {
          clientId = clientCaller.getClientId();
          final TLlamaAMNotificationRequest request = 
              TypeUtils.toAMNotification(event, nodeMapper);
          Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
            @Override
            public Object run() throws Exception {
              clientCaller.execute(new ClientCaller.Callable<Void>() {
                @Override
                public Void call() throws ClientException {
                  try {
                    TLlamaAMNotificationResponse response =
                        getClient().AMNotification(request);
                    if (!TypeUtils.isOK(response.getStatus())) {
                      LOG.warn(
                          "Client notification rejected status '{}', reason: {}",
                          response.getStatus().getStatus_code(),
                          response.getStatus().getError_msgs());
                    }
                  } catch (TException ex) {
                    throw new ClientException(ex);
                  }
                  return null;
                }
              });
              return null; 
            }
          });
        } else {
          LOG.warn("Handle '{}' not known, client notification discarded", 
              handle);
        }
      } catch (Exception ex) {
        if (retries >= ClientNotifier.this.maxRetries) {
          retries++;
          LOG.warn("Notification to '{}' failed on '{}' attempt, retrying in " +
              "'{}' ms, error: {}", new Object[] {clientId, retries, 
              ClientNotifier.this.retryInverval, ex.toString(), ex});
          relativeDelay = ClientNotifier.this.retryInverval;
          absoluteDelay = System.currentTimeMillis();
          ClientNotifier.this.eventsQueue.add(this);
        } else {
          LOG.warn("Notification to '{}' failed on '{}' attempt, releasing " +
              "client, error: {}", new Object[] {clientId, retries, 
              ex.toString(), ex});
          clientNotificationService.onMaxFailures(handle);
        }
      }
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(relativeDelay, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
      Notifier other = (Notifier) o;
      return (int) ((absoluteDelay + relativeDelay) -
          (other.absoluteDelay + other.relativeDelay));
    }
  }
  
}
