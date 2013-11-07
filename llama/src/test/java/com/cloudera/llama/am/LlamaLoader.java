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

import com.cloudera.llama.server.AbstractMain;
import com.cloudera.llama.server.Security;
import com.cloudera.llama.server.TypeUtils;
import com.cloudera.llama.thrift.LlamaAMService;
import com.cloudera.llama.thrift.TLlamaAMGetNodesRequest;
import com.cloudera.llama.thrift.TLlamaAMGetNodesResponse;
import com.cloudera.llama.thrift.TLlamaAMRegisterRequest;
import com.cloudera.llama.thrift.TLlamaAMRegisterResponse;
import com.cloudera.llama.thrift.TLlamaAMReservationRequest;
import com.cloudera.llama.thrift.TLlamaAMReservationResponse;
import com.cloudera.llama.thrift.TLlamaAMUnregisterRequest;
import com.cloudera.llama.thrift.TLlamaAMUnregisterResponse;
import com.cloudera.llama.thrift.TLlamaServiceVersion;
import com.cloudera.llama.thrift.TLocationEnforcement;
import com.cloudera.llama.thrift.TNetworkAddress;
import com.cloudera.llama.thrift.TResource;
import com.cloudera.llama.thrift.TStatusCode;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.Timer;
import org.apache.log4j.LogManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import javax.security.auth.Subject;
import javax.security.sasl.Sasl;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LlamaLoader {
  private static final String HELP_CMD = "help";
  private static final String UUID_CMD = "run";
  private static final String REGISTER_CMD = "register";
  private static final String UNREGISTER_CMD = "unregister";
  private static final String GET_NODES_CMD = "getnodes";
  private static final String RESERVE_CMD = "reserve";
  private static final String RELEASE_CMD = "release";
  private static final String CALLBACK_SERVER_CMD = "callbackserver";

  private static final String LLAMA = "llama";
  private static final String CLIENT_ID = "clientid";
  private static final String HANDLE = "handle";
  private static final String SECURE = "secure";
  private static final String CALLBACK = "callback";
  private static final String QUEUE = "queue";
  private static final String LOCATIONS = "locations";
  private static final String CPUS = "cpus";
  private static final String MEMORY = "memory";
  private static final String RELAX_LOCALITY = "relaxlocality";
  private static final String NO_GANG = "nogang";
  private static final String RESERVATION = "reservation";
  private static final String PORT = "port";

  static int clients = 100;
  static int reservations = 1000;
  static CountDownLatch startLatch = new CountDownLatch(1);
  static CountDownLatch endLatch = new CountDownLatch(clients);
  static Timer timer = new Timer();

  public static void main(String[] args) throws Exception {
    LogManager.resetConfiguration();
    int callbacksInitialPort = 20000;
    System.setProperty(AbstractMain.TEST_LLAMA_JVM_EXIT_SYS_PROP, "true");
    for (int i = 0; i < clients; i++) {
      startCallback(callbacksInitialPort + i);
    }
    Thread.sleep(1000);
    LlamaAMService.Client[] tClients = new LlamaAMService.Client[clients];
    for (int i = 0; i < clients; i++) {
      try {
        tClients[i] = createClient(false, "localhost", 15000);
      } catch (Throwable ex) {
        tClients[i] = null;
        System.out.println("Client [" + i + "] failed creation: " + ex);
      }
    }
    UUID[] handles = new UUID[clients];
    for (int i = 0; i < clients; i++) {
      if (tClients[i] != null) {
        try {
          handles[i] = register(tClients[i], UUID.randomUUID(), "localhost",
              callbacksInitialPort + i);
        } catch (Throwable ex) {
          tClients[i] = null;
          System.out.print("Client [" + i + "] failed creation: " + ex);
        }
      }
    }
    long start = System.currentTimeMillis();
    for (int i = 0; i < clients; i++) {
      if (tClients[i] != null) {
        reserve(tClients[i], handles[i], reservations);
      }
    }
    startLatch.countDown();
    endLatch.await();
    //Thread.sleep(10000);
    long end = System.currentTimeMillis();
    System.out.println("Real time: " + (end - start));
    System.out.println("Rate Mean: " + timer.getMeanRate());
    System.out.println("Timer Mean: " + timer.getSnapshot().getMean() / 1000000);
    for (int i = 0; i < clients; i++) {
      if (tClients[i] != null) {
        unregister(tClients[i], handles[i]);
      }
    }
    System.exit(0);
  }

  static void startCallback(final int port) throws Exception {
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          LlamaClient.runCallbackServer(false, port);
        } catch (Exception ex) {
          System.out.print(ex);
          throw new RuntimeException(ex);
        }
      }
    };
    t.setDaemon(true);
    t.start();
  }

  static Subject getSubject(boolean secure) throws Exception {
    return (secure) ? Security.loginClientFromKinit() : new Subject();
  }

  static LlamaAMService.Client createClient(final boolean secure,
      final String host, final int port) throws Exception {
    return Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<LlamaAMService.Client>() {
          @Override
          public LlamaAMService.Client run() throws Exception {
            TTransport transport = new TSocket(host, port);
            if (secure) {
              Map<String, String> saslProperties = new HashMap<String, String>();
              saslProperties.put(Sasl.QOP, "auth-conf");
              transport = new TSaslClientTransport("GSSAPI", null, "llama", host,
                  saslProperties, null, transport);
            }
            transport.open();
            TProtocol protocol = new TBinaryProtocol(transport);
            return new LlamaAMService.Client(protocol);
          }
        });
  }

  static UUID register(LlamaAMService.Client client, final UUID clientId,
      final String callbackHost, final int callbackPort) throws Exception {
    TLlamaAMRegisterRequest req = new TLlamaAMRegisterRequest();
    req.setVersion(TLlamaServiceVersion.V1);
    req.setClient_id(TypeUtils.toTUniqueId(clientId));
    TNetworkAddress tAddress = new TNetworkAddress();
    tAddress.setHostname(callbackHost);
    tAddress.setPort(callbackPort);
    req.setNotification_callback_service(tAddress);
    TLlamaAMRegisterResponse res = null;
    try {
      res = client.Register(req);
    } catch (Throwable ex) {
      System.out.println(ex);
    }
    if (res.getStatus().getStatus_code() != TStatusCode.OK) {
      throw new RuntimeException(res.toString());
    }
    return TypeUtils.toUUID(res.getAm_handle());
  }

  static void unregister(LlamaAMService.Client client, final UUID handle)
      throws Exception {
    TLlamaAMUnregisterRequest req = new TLlamaAMUnregisterRequest();
    req.setVersion(TLlamaServiceVersion.V1);
    req.setAm_handle(TypeUtils.toTUniqueId(handle));
    TLlamaAMUnregisterResponse res = client.Unregister(req);
    if (res.getStatus().getStatus_code() != TStatusCode.OK) {
      throw new RuntimeException(res.toString());
    }
  }


  static UUID reserve(LlamaAMService.Client client, UUID handle, String user,
      String queue, String[] locations, int cpus, int memory,
      boolean relaxLocality, boolean gang) throws Exception {
    TLlamaAMReservationRequest req = new TLlamaAMReservationRequest();
    req.setVersion(TLlamaServiceVersion.V1);
    req.setAm_handle(TypeUtils.toTUniqueId(handle));
    req.setUser(user);
    req.setQueue(queue);
    req.setGang(gang);
    List<TResource> resources = new ArrayList<TResource>();
    for (String location : locations) {
      TResource resource = new TResource();
      resource.setClient_resource_id(TypeUtils.toTUniqueId(
          UUID.randomUUID()));
      resource.setAskedLocation(location);
      resource.setV_cpu_cores((short) cpus);
      resource.setMemory_mb(memory);
      resource.setEnforcement((relaxLocality)
                              ? TLocationEnforcement.PREFERRED
                              : TLocationEnforcement.MUST);
      resources.add(resource);
    }
    req.setResources(resources);
    TLlamaAMReservationResponse res = client.Reserve(req);
    if (res.getStatus().getStatus_code() != TStatusCode.OK) {
      throw new RuntimeException(res.toString());
    }
    return TypeUtils.toUUID(res.getReservation_id());
  }

  static void reserve(final LlamaAMService.Client client, final UUID handle,
      final int loop) throws Exception {
    new Thread() {
      @Override
      public void run() {
        try {
          TLlamaAMGetNodesRequest req = new TLlamaAMGetNodesRequest();
          req.setVersion(TLlamaServiceVersion.V1);
          req.setAm_handle(TypeUtils.toTUniqueId(handle));
          TLlamaAMGetNodesResponse resp = client.GetNodes(req);
          startLatch.await();
          for (int i = 0; i < loop; i++) {
            long start = System.currentTimeMillis();
            reserve(client, handle, "user1", "queue1",
                new String[]{resp.getNodes().get(0)}, 1, 1, false, true);
            long end = System.currentTimeMillis();
            timer.update(end - start, TimeUnit.MILLISECONDS);
//            System.out.println("#####" + (end - start));
//            Thread.sleep(100);
          }
          endLatch.countDown();
        } catch (Exception ex) {
          System.out.print(ex);
          throw new RuntimeException(ex);
        }
      }
    }.start();
  }

}
