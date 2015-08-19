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
import com.cloudera.llama.server.AbstractServer;
import com.cloudera.llama.server.LlamaClientCallback;
import com.cloudera.llama.server.Security;
import com.cloudera.llama.server.TypeUtils;
import com.cloudera.llama.thrift.LlamaAMService;
import com.cloudera.llama.thrift.LlamaAMService.Client;
import com.cloudera.llama.thrift.TLlamaAMGetNodesRequest;
import com.cloudera.llama.thrift.TLlamaAMGetNodesResponse;
import com.cloudera.llama.thrift.TLlamaAMRegisterRequest;
import com.cloudera.llama.thrift.TLlamaAMRegisterResponse;
import com.cloudera.llama.thrift.TLlamaAMReleaseRequest;
import com.cloudera.llama.thrift.TLlamaAMReleaseResponse;
import com.cloudera.llama.thrift.TLlamaAMReservationExpansionRequest;
import com.cloudera.llama.thrift.TLlamaAMReservationExpansionResponse;
import com.cloudera.llama.thrift.TLlamaAMReservationRequest;
import com.cloudera.llama.thrift.TLlamaAMReservationResponse;
import com.cloudera.llama.thrift.TLlamaAMUnregisterRequest;
import com.cloudera.llama.thrift.TLlamaAMUnregisterResponse;
import com.cloudera.llama.thrift.TLlamaServiceVersion;
import com.cloudera.llama.thrift.TLocationEnforcement;
import com.cloudera.llama.thrift.TNetworkAddress;
import com.cloudera.llama.thrift.TResource;
import com.cloudera.llama.thrift.TStatusCode;
import com.cloudera.llama.util.CLIParser;
import com.cloudera.llama.util.UUID;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PatternOptionBuilder;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.sasl.Sasl;
import java.net.ConnectException;
import java.net.Socket;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LlamaClient {

  private static final String HELP_CMD = "help";
  private static final String UUID_CMD = "uuid";
  private static final String REGISTER_CMD = "register";
  private static final String UNREGISTER_CMD = "unregister";
  private static final String GET_NODES_CMD = "getnodes";
  private static final String RESERVE_CMD = "reserve";
  private static final String RELEASE_CMD = "release";
  private static final String EXPAND_CMD = "expand";
  private static final String CALLBACK_SERVER_CMD = "callbackserver";
  private static final String LOAD_CMD = "load";

  private static final String NO_LOG = "nolog";
  private static final String LLAMA = "llama";
  private static final String CLIENT_ID = "clientid";
  private static final String HANDLE = "handle";
  private static final String SECURE = "secure";
  private static final String CALLBACK = "callback";
  private static final String USER = "user";
  private static final String QUEUE = "queue";
  private static final String LOCATIONS = "locations";
  private static final String CPUS = "cpus";
  private static final String MEMORY = "memory";
  private static final String RELAX_LOCALITY = "relaxlocality";
  private static final String NO_GANG = "nogang";
  private static final String RESERVATION = "reservation";
  private static final String PORT = "port";

  private static final String CLIENTS = "clients";
  private static final String ROUNDS = "rounds";
  private static final String HOLD_TIME = "holdtime";
  private static final String EXPAND_TIME = "expandtime";
  private static final String SLEEP_TIME = "sleeptime";
  private static final String ALLOCATION_TIMEOUT = "allocationtimeout";
  private static final String EXPANSION_TIMEOUT = "expansionAllocationTimeout";

  private static CLIParser createParser() {
    CLIParser parser = new CLIParser("llamaclient", new String[0]);

    Option noLog = new Option(NO_LOG, false, "no logging");
    noLog.setRequired(false);
    Option llama = new Option(LLAMA, true, "<HOST>:<PORT> of llama");
    llama.setRequired(true);
    Option secure = new Option(SECURE, false, "uses kerberos");
    secure.setRequired(false);
    Option clientId = new Option(CLIENT_ID, true, "client ID");
    clientId.setRequired(true);
    Option callback = new Option(CALLBACK, true,
        "<HOST>:<PORT> of client's callback server");
    callback.setRequired(true);
    Option handle = new Option(HANDLE, true, "<UUID> from registration");
    handle.setRequired(true);
    Option user = new Option(USER, true, "reservation user");
    user.setRequired(true);
    Option queue = new Option(QUEUE, true, "reservation queue");
    queue.setRequired(false);
    Option locations = new Option(LOCATIONS, true,
        "locations of reservation, comma separated");
    locations.setRequired(true);
    Option cpus = new Option(CPUS, true,
        "cpus required per location of reservation");
    cpus.setRequired(true);
    cpus.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option memory = new Option(MEMORY, true,
        "memory (MB) required per location of reservation");
    memory.setRequired(true);
    memory.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option noGang = new Option(NO_GANG, false,
        "no gang reservation");
    noGang.setRequired(false);
    Option relaxLocality = new Option(RELAX_LOCALITY, false,
        "relax locality");
    relaxLocality.setRequired(false);
    Option reservation = new Option(RESERVATION, true,
        "<UUID> from reservation");
    reservation.setRequired(true);
    Option port = new Option(PORT, true, "<PORT> of callback server");
    port.setRequired(true);
    port.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option clients = new Option(CLIENTS, true, "number of clients");
    clients.setRequired(true);
    clients.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option rounds = new Option(ROUNDS, true, "reservations per client");
    rounds.setRequired(true);
    rounds.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option holdTime = new Option(HOLD_TIME, true,
        "time to hold a reservation once allocated (-1 to not wait for " +
            "allocation and release immediately), millisecs");
    holdTime.setRequired(true);
    holdTime.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option expandTime = new Option(EXPAND_TIME, true,
        "time to expand a reservation and release once allocated " +
            "(-1 to not expand), millisecs");
    expandTime.setRequired(false);
    expandTime.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option sleepTime = new Option(SLEEP_TIME, true,
        "time to sleep between  reservations, millisecs");
    sleepTime.setRequired(true);
    sleepTime.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option allocationTimeout = new Option(ALLOCATION_TIMEOUT, true,
        "allocation timeout, millisecs (default 10000)");
    allocationTimeout.setRequired(false);
    allocationTimeout.setType(PatternOptionBuilder.NUMBER_VALUE);
    Option expansionAllocationTimeout = new Option(EXPANSION_TIMEOUT, true,
            "expansion timeout, millisecs (default 10000)");
    expansionAllocationTimeout.setRequired(false);
    expansionAllocationTimeout.setType(PatternOptionBuilder.NUMBER_VALUE);

    //help
    Options options = new Options();
    parser.addCommand(HELP_CMD, "",
        "display usage for all commands or specified command", options, false);

    //uuid
    options = new Options();
    parser.addCommand(UUID_CMD, "", "generate an UUID", options, false);

    //register
    options = new Options();
    options.addOption(noLog);
    options.addOption(llama);
    options.addOption(clientId);
    options.addOption(callback);
    options.addOption(secure);
    parser.addCommand(REGISTER_CMD, "", "register client", options, false);


    //unregister
    options = new Options();
    options.addOption(noLog);
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(secure);
    parser.addCommand(UNREGISTER_CMD, "", "unregister client", options, false);

    //get nodes
    options = new Options();
    options.addOption(noLog);
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(secure);
    parser.addCommand(GET_NODES_CMD, "", "get cluster nodes", options, false);

    //reservation
    options = new Options();
    options.addOption(noLog);
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(user);
    options.addOption(queue);
    options.addOption(locations);
    options.addOption(cpus);
    options.addOption(memory);
    options.addOption(relaxLocality);
    options.addOption(noGang);
    options.addOption(secure);
    parser.addCommand(RESERVE_CMD, "", "make a reservation", options, false);

    //expand
    options = new Options();
    options.addOption(noLog);
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(reservation);
    options.addOption(locations);
    options.addOption(cpus);
    options.addOption(memory);
    options.addOption(relaxLocality);
    options.addOption(secure);
    parser.addCommand(EXPAND_CMD, "", "expand a reservation", options, false);

    //release
    options = new Options();
    options.addOption(noLog);
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(reservation);
    options.addOption(secure);
    parser.addCommand(RELEASE_CMD, "", "release a reservation", options, false);

    //callback server
    options = new Options();
    options.addOption(noLog);
    options.addOption(port);
    options.addOption(secure);
    parser.addCommand(CALLBACK_SERVER_CMD, "", "run callback server", options,
        false);

    //load

    options = new Options();
    options.addOption(noLog);
    options.addOption(llama);
    options.addOption(clients);
    options.addOption(callback);
    options.addOption(rounds);
    options.addOption(holdTime);
    options.addOption(expandTime);
    options.addOption(sleepTime);
    options.addOption(user);
    options.addOption(queue);
    options.addOption(locations);
    options.addOption(cpus);
    options.addOption(memory);
    options.addOption(relaxLocality);
    options.addOption(noGang);
    options.addOption(allocationTimeout);
    parser.addCommand(LOAD_CMD, "", "run a load", options, false);

    return parser;
  }

  private static final Logger LOG = LoggerFactory.getLogger(LlamaClient.class);

  public static void main(String[] args) throws Exception {
    CLIParser parser = createParser();
    try {
      CLIParser.Command command = parser.parse(args);
      CommandLine cl = command.getCommandLine();
      boolean secure = cl.hasOption(SECURE);
      if (cl.hasOption(NO_LOG)) {
        System.setProperty("log4j.configuration", "log4j-null.properties");
      }
      //to force log4j initialization before anything happens.
      LoggerFactory.getLogger(LlamaClient.class);
      if (command.getName().equals(HELP_CMD)) {
        parser.showHelp(command.getCommandLine());
      } else if (command.getName().equals(UUID_CMD)) {
        System.out.println(UUID.randomUUID());
      } else if (command.getName().equals(REGISTER_CMD)) {
        UUID clientId = UUID.fromString(cl.getOptionValue(CLIENT_ID));
        String llama = cl.getOptionValue(LLAMA);
        String callback = cl.getOptionValue(CALLBACK);
        UUID handle = register(secure, getHost(llama), getPort(llama), clientId,
            getHost(callback), getPort(callback));
        System.out.println(handle);
      } else if (command.getName().equals(UNREGISTER_CMD)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        unregister(secure, getHost(llama), getPort(llama), handle);
      } else if (command.getName().equals(GET_NODES_CMD)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        List<String> nodes = getNodes(secure, getHost(llama), getPort(llama),
            handle);
        for (String node : nodes) {
          System.out.println(node);
        }
      } else if (command.getName().equals(RESERVE_CMD)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        String user = cl.getOptionValue(USER);
        String queue = cl.getOptionValue(QUEUE);
        String[] locations = cl.getOptionValue(LOCATIONS).split(",");
        int cpus = Integer.parseInt(cl.getOptionValue(CPUS));
        int memory = Integer.parseInt(cl.getOptionValue(MEMORY));
        boolean gang = !cl.hasOption(NO_GANG);
        boolean relaxLocality = cl.hasOption(RELAX_LOCALITY);

        UUID reservation = reserve(secure, getHost(llama), getPort(llama),
            handle, user, queue, locations, cpus, memory, relaxLocality, gang);
        System.out.println(reservation);
      } else if (command.getName().equals(EXPAND_CMD)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        UUID expansionOf = UUID.fromString(cl.getOptionValue(RESERVATION));
        String[] locations = cl.getOptionValue(LOCATIONS).split(",");
        if (locations == null || locations.length != 1) {
          System.err.println("Expansion can be done only for a single node.");
          System.err.println();
          System.err.println(parser.shortHelp());
          System.exit(1);
        }
        int cpus = Integer.parseInt(cl.getOptionValue(CPUS));
        int memory = Integer.parseInt(cl.getOptionValue(MEMORY));
        boolean relaxLocality = cl.hasOption(RELAX_LOCALITY);

        UUID reservation = expand(secure, getHost(llama), getPort(llama),
            handle, expansionOf, locations[0], cpus, memory, relaxLocality);
        System.out.println(reservation);
      } else if (command.getName().equals(RELEASE_CMD)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        UUID reservation = UUID.fromString(cl.getOptionValue(RESERVATION));
        release(secure, getHost(llama), getPort(llama), handle, reservation);
      } else if (command.getName().equals(CALLBACK_SERVER_CMD)) {
        int port = Integer.parseInt(cl.getOptionValue(PORT));
        runCallbackServer(secure, port);
      } else if (command.getName().endsWith(LOAD_CMD)) {
        String llama = cl.getOptionValue(LLAMA);
        int clients = Integer.parseInt(cl.getOptionValue(CLIENTS));
        String callback = cl.getOptionValue(CALLBACK);
        int rounds = Integer.parseInt(cl.getOptionValue(ROUNDS));
        int holdTime = Integer.parseInt(cl.getOptionValue(HOLD_TIME));
        int expandTime = (cl.hasOption(EXPAND_TIME))?
          Integer.parseInt(cl.getOptionValue(EXPAND_TIME)) : -1;
        int sleepTime = Integer.parseInt(cl.getOptionValue(SLEEP_TIME));
        String user = cl.getOptionValue(USER);
        String queue = cl.getOptionValue(QUEUE);
        String[] locations = cl.getOptionValue(LOCATIONS).split(",");
        int cpus = Integer.parseInt(cl.getOptionValue(CPUS));
        int memory = Integer.parseInt(cl.getOptionValue(MEMORY));
        boolean gang = !cl.hasOption(NO_GANG);
        boolean relaxLocality = cl.hasOption(RELAX_LOCALITY);
        int allocationTimeout = (cl.hasOption(ALLOCATION_TIMEOUT))
            ? Integer.parseInt(cl.getOptionValue(ALLOCATION_TIMEOUT)) : 10000;
        int expansionAllocationTimeout = (cl.hasOption(EXPANSION_TIMEOUT))
            ? Integer.parseInt(cl.getOptionValue(EXPANSION_TIMEOUT)) : 10000;
        runLoad(secure, getHost(llama), getPort(llama), clients,
            getHost(callback), getPort(callback), rounds, holdTime, expandTime,
            sleepTime, user, queue, locations, relaxLocality, cpus, memory,
            gang, allocationTimeout, expansionAllocationTimeout);
      } else {
        System.err.println("Missing sub-command");
        System.err.println();
        System.err.println(parser.shortHelp());
        System.exit(1);
      }
      System.exit(0);
    } catch (ParseException ex) {
      System.err.println("Invalid invocation: " + ex.getMessage());
      System.err.println();
      System.err.println(parser.shortHelp());
      System.exit(1);
    } catch (Throwable ex) {
      System.err.println("Error: " + ex.getMessage());
      ex.printStackTrace(System.err);
      System.exit(2);
    }
  }

  private static String getHost(String value) {
    int colon = value.indexOf(":");
    if (colon == -1) {
      throw new IllegalArgumentException(value + " must be <HOST>:<PORT>");
    }
    return value.substring(0, colon);
  }

  private static int getPort(String value) {
    int colon = value.indexOf(":");
    if (colon == -1) {
      throw new IllegalArgumentException(value + " must be <HOST>:<PORT>");
    }
    return Integer.parseInt(value.substring(colon + 1));
  }

  static Subject getSubject(boolean secure) throws Exception {
    return (secure) ? Security.loginClientFromKinit() : new Subject();
  }

  static LlamaAMService.Client createClient(boolean secure, String host,
      int port) throws Exception {
    TTransport transport = new TSocket(host, port);
    if (secure) {
      Map<String, String> saslProperties = new HashMap<String, String>();
      saslProperties.put(Sasl.QOP, "auth-conf,auth-int,auth");
      transport = new TSaslClientTransport("GSSAPI", null, "llama", host,
          saslProperties, null, transport);
    }
    transport.open();
    TProtocol protocol = new TBinaryProtocol(transport);
    return new LlamaAMService.Client(protocol);
  }

  static UUID register(LlamaAMService.Client client, UUID clientId,
      String callbackHost, int callbackPort) throws Exception {
    TLlamaAMRegisterRequest req = new TLlamaAMRegisterRequest();
    req.setVersion(TLlamaServiceVersion.V1);
    req.setClient_id(TypeUtils.toTUniqueId(clientId));
    TNetworkAddress tAddress = new TNetworkAddress();
    tAddress.setHostname(callbackHost);
    tAddress.setPort(callbackPort);
    req.setNotification_callback_service(tAddress);
    TLlamaAMRegisterResponse res = client.Register(req);
    if (res.getStatus().getStatus_code() != TStatusCode.OK) {
      throw new RuntimeException(res.toString());
    }
    return TypeUtils.toUUID(res.getAm_handle());
  }

  static UUID register(final boolean secure, final String llamaHost,
      final int llamaPort, final UUID clientId, final String callbackHost,
      final int callbackPort) throws Exception {
    return Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<UUID>() {
          @Override
          public UUID run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            return register(client, clientId, callbackHost, callbackPort);
          }
        });
  }

  static void unregister(LlamaAMService.Client client, UUID handle)
      throws Exception {
    TLlamaAMUnregisterRequest req = new TLlamaAMUnregisterRequest();
    req.setVersion(TLlamaServiceVersion.V1);
    req.setAm_handle(TypeUtils.toTUniqueId(handle));
    TLlamaAMUnregisterResponse res = client.Unregister(req);
    if (res.getStatus().getStatus_code() != TStatusCode.OK) {
      throw new RuntimeException(res.toString());
    }
  }

  static void unregister(final boolean secure, final String llamaHost,
      final int llamaPort, final UUID handle) throws Exception {
    Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            unregister(client, handle);
            return null;
          }
        });
  }

  static List<String> getNodes(final boolean secure, final String llamaHost,
      final int llamaPort, final UUID handle) throws Exception {
    return Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<List<String>>() {
          @Override
          public List<String> run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            TLlamaAMGetNodesRequest req = new TLlamaAMGetNodesRequest();
            req.setVersion(TLlamaServiceVersion.V1);
            req.setAm_handle(TypeUtils.toTUniqueId(handle));
            TLlamaAMGetNodesResponse res = client.GetNodes(req);
            if (res.getStatus().getStatus_code() != TStatusCode.OK) {
              throw new RuntimeException(res.toString());
            }
            return res.getNodes();
          }
        });
  }

  static UUID reserve(LlamaAMService.Client client, UUID handle,
      String user, String queue, String[] locations, boolean relaxLocality,
      int cpus, int memory, boolean gang) throws Exception {
    return reserve(client, handle, UUID.randomUUID(), user, queue, locations,
        relaxLocality, cpus, memory, gang);
  }

  static UUID reserve(LlamaAMService.Client client, UUID handle,
      UUID reservationId, String user,
      String queue, String[] locations, boolean relaxLocality, int cpus,
      int memory,
      boolean gang) throws Exception {
    TLlamaAMReservationRequest req = new TLlamaAMReservationRequest();
    req.setVersion(TLlamaServiceVersion.V1);
    req.setAm_handle(TypeUtils.toTUniqueId(handle));
    req.setUser(user);
    req.setQueue(queue);
    req.setReservation_id(TypeUtils.toTUniqueId(reservationId));
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
      String status = res.getStatus().getStatus_code().toString();
      int code = (res.getStatus().isSetError_code())
                 ? res.getStatus().getError_code() : 0;
      String msg = (res.getStatus().isSetError_msgs())
          ? res.getStatus().getError_msgs().get(0) : "";
      throw new RuntimeException(status + " - " + code + " - " + msg);
    }
    return TypeUtils.toUUID(res.getReservation_id());
  }

  static UUID expand(Client client, UUID handle, UUID reservation,
      String location, boolean relaxLocality, int cpus,
      int memory) throws Exception {
    return expand(client, handle, reservation, UUID.randomUUID(), location,
        relaxLocality, cpus, memory);
  }

  static UUID expand(Client client, UUID handle, UUID reservation,
      UUID expansion, String location, boolean relaxLocality, int cpus,
      int memory) throws Exception {
    TLlamaAMReservationExpansionRequest req = new TLlamaAMReservationExpansionRequest();
    req.setVersion(TLlamaServiceVersion.V1);
    req.setAm_handle(TypeUtils.toTUniqueId(handle));
    req.setExpansion_of(TypeUtils.toTUniqueId(reservation));

    TResource resource = new TResource();
    resource.setClient_resource_id(TypeUtils.toTUniqueId(
        UUID.randomUUID()));
    resource.setAskedLocation(location);
    resource.setV_cpu_cores((short) cpus);
    resource.setMemory_mb(memory);
    resource.setEnforcement((relaxLocality)
        ? TLocationEnforcement.PREFERRED
        : TLocationEnforcement.MUST);

    req.setResource(resource);
    req.setExpansion_id(TypeUtils.toTUniqueId(expansion));
    TLlamaAMReservationExpansionResponse res = client.Expand(req);
    if (res.getStatus().getStatus_code() != TStatusCode.OK) {
      String status = res.getStatus().getStatus_code().toString();
      int code = (res.getStatus().isSetError_code())
          ? res.getStatus().getError_code() : 0;
      String msg = (res.getStatus().isSetError_msgs())
          ? res.getStatus().getError_msgs().get(0) : "";
      throw new RuntimeException(status + " - " + code + " - " + msg);
    }
    return TypeUtils.toUUID(res.getReservation_id());
  }

  static UUID reserve(final boolean secure, final String llamaHost,
      final int llamaPort, final UUID handle, final String user,
      final String queue, final String[] locations, final int cpus,
      final int memory, final boolean relaxLocality, final boolean gang)
      throws Exception {
    return Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<UUID>() {
          @Override
          public UUID run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            return reserve(client, handle, user, queue, locations,
              relaxLocality, cpus, memory, gang);
          }
        });
  }

  static UUID expand(final boolean secure, final String llamaHost,
      final int llamaPort, final UUID handle, final UUID reservation,
      final String location, final int cpus,
      final int memory, final boolean relaxLocality)
      throws Exception {
    return Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<UUID>() {
          @Override
          public UUID run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            return expand(client, handle, reservation, location,
                relaxLocality, cpus, memory);
          }
        });
  }

  static void release(LlamaAMService.Client client, UUID handle,
      UUID reservation) throws Exception {
    TLlamaAMReleaseRequest req = new TLlamaAMReleaseRequest();
    req.setVersion(TLlamaServiceVersion.V1);
    req.setAm_handle(TypeUtils.toTUniqueId(handle));
    req.setReservation_id(TypeUtils.toTUniqueId(reservation));
    TLlamaAMReleaseResponse res = client.Release(req);
    if (res.getStatus().getStatus_code() != TStatusCode.OK) {
      throw new RuntimeException(res.toString());
    }
  }

  static void release(final boolean secure, final String llamaHost,
      final int llamaPort, final UUID handle, final UUID reservation)
      throws Exception {
    Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            release(client, handle, reservation);
            return null;
          }
        });
  }

  public static class LlamaClientCallbackMain extends AbstractMain {

    @Override
    protected Class<? extends AbstractServer> getServerClass() {
      return LlamaClientCallback.class;
    }
  }

  static void runCallbackServer(final boolean secure, final int port)
      throws Exception {
    Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() throws Exception {
            new LlamaClientCallbackMain().run(new String[]
                { "-D" + LlamaClientCallback.PORT_KEY + "=" + port});
            return null;
          }
        });
  }

  static void runLoad(final boolean secure, String llamaHost, int llamaPort,
      int clients, String callbackHost, int callbackStartPort, int rounds,
      int holdTime, int expandTime, int sleepTime, String user, String queue,
      String[] locations, boolean relaxLocality, int cpus, int memory,
      boolean gang, int allocationTimeout, int expansionAllocationTimeout)
      throws Exception {

    //start callback servers
    for (int i = 0; i < clients; i++) {
      final int callbackPort = callbackStartPort + i;
      Thread t = new Thread("callback@" + (callbackStartPort + i)) {
        @Override
        public void run() {
          try {
            runCallbackServer(secure, callbackPort);
          } catch (Exception ex) {
            System.out.print(ex);
            throw new RuntimeException(ex);
          }
        }
      };
      t.setDaemon(true);
      t.start();
    }
    //waiting until all callback servers are up.
    long start = System.currentTimeMillis();
    long waitTime = 30 * 1000;
    for (int i = 0; i < clients; i++) {
      long individualWaitTime = 500;
      long counts = waitTime / individualWaitTime;
      for (int attempts = 0; attempts < counts; attempts++) {
        try {
          new Socket(callbackHost, callbackStartPort + i).close();
          break;
        } catch (ConnectException ex) {
          if (System.currentTimeMillis() - start > waitTime) {
            final String error = "Callback servers cannot start, timedout";
            System.out.println(error);
            throw new RuntimeException(error, ex);
          } else {
            System.out.println("Sleep for client " + i);
            Thread.sleep(individualWaitTime);
          }
        }
      }
    }
    start = System.currentTimeMillis();
    CountDownLatch registerLatch = new CountDownLatch(clients);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(clients);
    AtomicInteger reservationErrorCount = new AtomicInteger();
    AtomicInteger expansionErrorCount = new AtomicInteger();
    Timer[] timers = new Timer[TIMERS_COUNT];
    for (int i = 0; i < TIMERS_COUNT; i++) {
      timers[i] = new Timer();
    }
    AtomicInteger allocationTimeouts = new AtomicInteger();
    AtomicInteger expansionAllocationTimeouts = new AtomicInteger();
    for (int i = 0; i < clients; i++) {
      runClientLoad(secure, llamaHost, llamaPort, callbackHost,
          callbackStartPort + i, rounds, holdTime, expandTime, sleepTime, user,
          queue, locations, relaxLocality, cpus, memory, gang, registerLatch,
          startLatch, endLatch, allocationTimeout, expansionAllocationTimeout, timers,
          allocationTimeouts, expansionAllocationTimeouts, reservationErrorCount,
          expansionErrorCount);
    }
    registerLatch.await();
    startLatch.countDown();
    endLatch.await();
    long end = System.currentTimeMillis();
    System.out.println();
    System.out.println("Llama load run: ");
    System.out.println();
    System.out.println("  Number of clients         : " + clients);
    System.out.println("  Reservations per client   : " + rounds);
    System.out.println("  Hold allocations for      : " + holdTime + " ms");
    System.out.println("  Expand allocations for    : " + expandTime + " ms");
    System.out.println("  Sleep between reservations: " + sleepTime + " ms");
    System.out.println("  Allocation timeout        : " + allocationTimeout +
        " ms");
    System.out.println("  Expansion timeout         : " + expansionAllocationTimeout +
        " ms");
    System.out.println();
    System.out.println("  Wall time                 : " + (end - start) + " ms");
    System.out.println();
    System.out.println("  Reservation errors        : " + reservationErrorCount);
    System.out.println();
    System.out.println("  Timed out allocations     : " + allocationTimeouts);
    System.out.println();
    System.out.println("  Expansion errors          : " + expansionErrorCount);
    System.out.println();
    System.out.println("  Timed out expansion allocations     : " +
            expansionAllocationTimeouts);
    System.out.println();
    System.out.println("  Reservation rate          : " +
        timers[TIMER_TYPE.RESERVE.ordinal()].getMeanRate() + " per sec");
    System.out.println("  Expansion rate            : " +
        timers[TIMER_TYPE.EXPAND.ordinal()].getMeanRate() + " per sec");
    System.out.println();
    System.out.println("  Client Register RPC time   (mean)    : " +
        timers[TIMER_TYPE.REGISTER.ordinal()].getSnapshot().getMean() /
            1000000 + " ms");
    System.out.println("  Reserve RPC time    (mean)    : " +
        timers[TIMER_TYPE.RESERVE.ordinal()].getSnapshot().getMean() /
            1000000 + " ms");
    printTimerDetails("Initial Reservation Allocation time",
        timers[TIMER_TYPE.ALLOCATE.ordinal()]);

    System.out.println("  Expand RPC time   (mean)      : " +
        timers[TIMER_TYPE.EXPAND.ordinal()].getSnapshot().getMean() / 1000000
        + " ms");
    printTimerDetails("Expansion Allocation time",
        timers[TIMER_TYPE.EXPANSIONALLOCATE.ordinal()]);

    System.out.println("  Expansion release RPC time (mean)    : " +
        timers[TIMER_TYPE.EXPANSIONRELEASE.ordinal()].getSnapshot().getMean() /
          1000000 + " ms");
    System.out.println("  Release RPC time    (mean)    : " +
        timers[TIMER_TYPE.RELEASE.ordinal()].getSnapshot().getMean() / 1000000 + " ms");
    System.out.println("  Unregister RPC time (mean)    : " +
        timers[TIMER_TYPE.UNREGISTER.ordinal()].getSnapshot().getMean() / 1000000 + " ms");
    System.out.println();
  }

  private static void printTimerDetails(String name, Timer timer) {
    System.out.println("  " + name + "    (details) :");
    System.out.println("     Count            : " +
        timer.getCount());
    Snapshot snapshot = timer.getSnapshot();
    System.out.println("     Mean " + snapshot.getMean() / 1000000 + " ms");
    System.out.println("     75th percentile " +
        snapshot.get75thPercentile() / 1000000 + " ms");
    System.out.println("     99th percentile " +
        snapshot.get99thPercentile() / 1000000 + " ms");
    System.out.println("     999th percentile " +
        snapshot.get999thPercentile() / 1000000 + " ms");
    System.out.println();
  }

  private static final AtomicInteger CLIENT_CALLS_COUNT = new AtomicInteger();

  private static void tickClientCall() {
    int ticks = CLIENT_CALLS_COUNT.incrementAndGet();
    if (ticks % 100 == 0) {
      System.out.println("  Client calls: " + ticks);
    }
  }

  private enum TIMER_TYPE {
    REGISTER,
    RESERVE,
    ALLOCATE,
    RELEASE,
    UNREGISTER,
    EXPAND,
    EXPANSIONALLOCATE,
    EXPANSIONRELEASE,
  }
  private static final int TIMERS_COUNT = TIMER_TYPE.values().length;

  static void runClientLoad(final boolean secure, final String llamaHost,
      final int llamaPort, final String callbackHost, final int callbackPort,
      final int rounds, final int holdTime, final int expandTime, final int sleepTime,
      final String user, final String queue, final String[] locations,
      final boolean relaxLocality, final int cpus, final int memory, final boolean gang,
      final CountDownLatch registerLatch, final CountDownLatch startLatch,
      final CountDownLatch endLatch, final int allocationTimeout,
      final int expansionAllocationTimeout, final Timer[] timers,
      final AtomicInteger allocationTimeouts,
      final AtomicInteger expansionAllocationTimeouts,
      final AtomicInteger reservationErrorCount,
      final AtomicInteger expansionErrorCount) {
    Thread t = new Thread("client@" + callbackPort) {
      @Override
      public void run() {
        try {
          Subject.doAs(getSubject(secure),
              new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws Exception {
                  try {
                    //create client
                    LlamaAMService.Client client = createClient(secure,
                        llamaHost, llamaPort);

                    //register
                    tickClientCall();

                    long start = System.currentTimeMillis();
                    UUID handle = register(client, UUID.randomUUID(),
                        callbackHost, callbackPort);
                    long end = System.currentTimeMillis();
                    timers[TIMER_TYPE.REGISTER.ordinal()].update(
                        end - start, TimeUnit.MILLISECONDS);

                    registerLatch.countDown();
                    startLatch.await();
                    for (int i = 0; i < rounds; i++) {

                      //reserve
                      tickClientCall();

                      UUID reservation = UUID.randomUUID();
                      CountDownLatch latch =
                        LlamaClientCallback.createReservationLatch(reservation);
                      try {
                        start = System.currentTimeMillis();
                        reservation = reserve(client, handle, reservation, user,
                            queue, locations, relaxLocality, cpus, memory, gang);
                        end = System.currentTimeMillis();
                        timers[TIMER_TYPE.RESERVE.ordinal()].update(
                            end - start, TimeUnit.MILLISECONDS);
                      } catch (RuntimeException ex) {
                        reservationErrorCount.incrementAndGet();
                        System.out.println("ERROR while reserve(): " + ex);
                      }

                      waitOnReservationOrExpansion(reservation, holdTime,
                          allocationTimeouts, TIMER_TYPE.ALLOCATE.ordinal(),
                          allocationTimeout, latch);

                      if (expandTime >= 0) {
                        for (final String location : locations) {
                          try {
                            int expandedCpu = cpus;
                            int expandedMemory = memory;

                            UUID expansion = UUID.randomUUID();
                            CountDownLatch expansionLatch =
                              LlamaClientCallback.createReservationLatch(
                                  expansion);

                            //expand
                            tickClientCall();
                            try {
                              start = System.currentTimeMillis();
                              expansion = expand(client, handle, reservation,
                                      expansion, location, false, expandedCpu,
                                      expandedMemory);
                              end = System.currentTimeMillis();
                              timers[TIMER_TYPE.EXPAND.ordinal()].update(
                                  end - start, TimeUnit.MILLISECONDS);
                            } catch (RuntimeException ex) {
                              expansionErrorCount.incrementAndGet();
                              System.out.println("ERROR while expand(): " + ex);
                            }

                            waitOnReservationOrExpansion(expansion, expandTime,
                                expansionAllocationTimeouts,
                                TIMER_TYPE.EXPANSIONALLOCATE.ordinal(),
                                expansionAllocationTimeout, expansionLatch);

                            if (expansion != null) {
                              // release expansion
                              tickClientCall();

                              start = System.currentTimeMillis();
                              release(client, handle, expansion);
                              end = System.currentTimeMillis();
                              timers[TIMER_TYPE.EXPANSIONRELEASE.ordinal()].
                                  update(end - start, TimeUnit.MILLISECONDS);
                            }
                          } catch (Exception ex) {
                            System.out.print(ex);
                            throw new RuntimeException(ex);
                          }
                        }
                      }

                      //release
                      tickClientCall();

                      if (reservation != null) {
                        start = System.currentTimeMillis();
                        release(client, handle, reservation);
                        end = System.currentTimeMillis();
                        timers[TIMER_TYPE.RELEASE.ordinal()].update(
                            end - start, TimeUnit.MILLISECONDS);
                      }

                      //sleep
                      Thread.sleep(sleepTime);
                    }

                    //unregister
                    tickClientCall();

                    start = System.currentTimeMillis();
                    unregister(client, handle);
                    end = System.currentTimeMillis();
                    timers[TIMER_TYPE.UNREGISTER.ordinal()].update(
                        end - start, TimeUnit.MILLISECONDS);

                    endLatch.countDown();
                  } catch (Exception ex) {
                    System.out.print(ex);
                    throw new RuntimeException(ex);
                  }
                  return null;
                }

                private void waitOnReservationOrExpansion(
                    UUID reservation, int timeToWait, AtomicInteger timeouts,
                    int timerType, int timeoutMsec, CountDownLatch latch)
                    throws InterruptedException {
                  long start;
                  long end;

                  if (timeToWait >=0 ) {
                    //wait allocation
                    start = System.currentTimeMillis();
                    if (latch != null &&
                        latch.await(timeoutMsec,
                            TimeUnit.MILLISECONDS)) {
                      end = System.currentTimeMillis();
                      timers[timerType].update(end - start,
                              TimeUnit.MILLISECONDS);
                    } else {
                      LOG.warn("Timed out on " + reservation);
                      timeouts.incrementAndGet();
                    }

                    //hold
                    Thread.sleep(timeToWait);
                  }
                }
              });
        } catch (Throwable ex) {
          System.out.println(ex.toString());
          ex.printStackTrace(System.out);
          System.exit(2);
        }
      }
    };
    t.setDaemon(true);
    t.start();

  }

}
