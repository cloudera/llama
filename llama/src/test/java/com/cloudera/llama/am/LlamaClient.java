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
import com.cloudera.llama.server.TypeUtils;
import com.cloudera.llama.thrift.LlamaAMService;
import com.cloudera.llama.thrift.TLlamaAMGetNodesRequest;
import com.cloudera.llama.thrift.TLlamaAMGetNodesResponse;
import com.cloudera.llama.thrift.TLlamaAMRegisterRequest;
import com.cloudera.llama.thrift.TLlamaAMRegisterResponse;
import com.cloudera.llama.thrift.TLlamaAMReleaseRequest;
import com.cloudera.llama.thrift.TLlamaAMReleaseResponse;
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

import javax.security.auth.Subject;
import javax.security.sasl.Sasl;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LlamaClient {

  private static final String HELP = "help";
  private static final String REGISTER = "register";
  private static final String UNREGISTER = "unregister";
  private static final String GET_NODES = "getnodes";
  private static final String RESERVE = "reserve";
  private static final String RELEASE = "release";
  private static final String CALLBACK_SERVER = "callbackserver";

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

  private static CLIParser createParser() {
    CLIParser parser = new CLIParser("llamaclient", new String[0]);

    Option llama = new Option(LLAMA, true, "<HOST>:<PORT> of llama");
    llama.setRequired(true);
    Option secure = new Option(SECURE, false, "uses kerberos");
    secure.setRequired(false);
    Option clientId = new Option(CLIENT_ID, true, "client ID");
    llama.setRequired(true);
    Option callback = new Option(CALLBACK, true,
        "<HOST>:<PORT> of client's callback server");
    callback.setRequired(true);
    Option handle = new Option(HANDLE, true, "<UUID> from registration");
    handle.setRequired(true);
    Option queue = new Option(QUEUE, true, "queue of reservation");
    queue.setRequired(true);
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

    //help
    Options options = new Options();
    parser.addCommand(HELP, "",
        "display usage for all commands or specified command", options, false);

    //register
    options = new Options();
    options.addOption(llama);
    options.addOption(clientId);
    options.addOption(callback);
    options.addOption(secure);
    parser.addCommand(REGISTER, "", "register client", options, false);

    //unregister
    options = new Options();
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(secure);
    parser.addCommand(UNREGISTER, "", "unregister client", options, false);

    //get nodes
    options = new Options();
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(secure);
    parser.addCommand(GET_NODES, "", "get cluster nodes", options, false);

    //reservation
    options = new Options();
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(queue);
    options.addOption(locations);
    options.addOption(cpus);
    options.addOption(memory);
    options.addOption(relaxLocality);
    options.addOption(noGang);
    options.addOption(secure);
    parser.addCommand(RESERVE, "", "make a reservation", options, false);

    //release
    options = new Options();
    options.addOption(llama);
    options.addOption(handle);
    options.addOption(reservation);
    options.addOption(secure);
    parser.addCommand(RELEASE, "", "release a reservation", options, false);

    //callback server
    options = new Options();
    options.addOption(port);
    options.addOption(secure);
    parser.addCommand(CALLBACK_SERVER, "", "run callback server", options,
        false);

    return parser;
  }

  // register   -llama HOST:PORT -callback HOST:PORT -clientid CLIENTID -secure >>> HANDLE
  // unregister -llama HOST:PORT -handle HANDLE -secure
  // getnodes   -llama HOST:PORT -handle HANDLE -secure
  // reserve    -llama HOST:PORT -handle HANDLE -queue QUEUE -locations L1,L2,.. -secure
  //            -cpus CPUS -memory MEMORY -gang BOOLEAN -secure >>> RESERVATION
  // release    -llama HOST:PORT -handle HANDLE -reservation RESERVATION -secure
  // callbackserver -port PORT -secure
  public static void main(String[] args) throws Exception {
    CLIParser parser = createParser();
    try {
      CLIParser.Command command = parser.parse(args);
      CommandLine cl = command.getCommandLine();
      boolean secure = cl.hasOption(SECURE);
      if (secure) {
        throw new UnsupportedOperationException("'-secure' is not supported");
      }

      if (command.getName().equals(HELP)) {
        parser.showHelp(command.getCommandLine());
      } else if (command.getName().equals(REGISTER)) {
        String clientId = cl.getOptionValue(CLIENT_ID);
        String llama = cl.getOptionValue(LLAMA);
        String callback = cl.getOptionValue(CALLBACK);
        UUID handle = register(secure, getHost(llama), getPort(llama), clientId,
            getHost(callback), getPort(callback));
        System.out.println(handle);

      } else if (command.getName().equals(UNREGISTER)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        unregister(secure, getHost(llama), getPort(llama), handle);
      } else if (command.getName().equals(GET_NODES)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        List<String> nodes = getNodes(secure, getHost(llama), getPort(llama),
            handle);
        for (String node : nodes) {
          System.out.println(node);
        }
      } else if (command.getName().equals(RESERVE)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        String queue = cl.getOptionValue(QUEUE);
        String[] locations = cl.getOptionValue(LOCATIONS).split(",");
        int cpus = Integer.parseInt(cl.getOptionValue(CPUS));
        int memory = Integer.parseInt(cl.getOptionValue(MEMORY));
        boolean gang = !cl.hasOption(NO_GANG);
        boolean relaxLocality = cl.hasOption(RELAX_LOCALITY);

        UUID reservation = reserve(secure, getHost(llama), getPort(llama),
            handle, queue, locations, cpus, memory, relaxLocality, gang);
        System.out.println(reservation);
      } else if (command.getName().equals(RELEASE)) {
        String llama = cl.getOptionValue(LLAMA);
        UUID handle = UUID.fromString(cl.getOptionValue(HANDLE));
        UUID reservation = UUID.fromString(cl.getOptionValue(RESERVATION));
        release(secure, getHost(llama), getPort(llama), handle, reservation);
      } else if (command.getName().equals(CALLBACK_SERVER)) {
        int port = Integer.parseInt(cl.getOptionValue(PORT));
        runCallbackServer(secure, port);
      }
      System.exit(0);
    } catch (ParseException ex) {
      System.err.println("Invalid sub-command: " + ex.getMessage());
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
    return new Subject();
//    if (secure) {
//      File keytab = new File(TestAbstractMain.createTestDir(),
//          "client.keytab");
//      Set<Principal> principals = new HashSet<Principal>();
//      principals.add(new KerberosPrincipal("client"));
//      LoginContext context = new LoginContext("", subject, null,
//          new Security.KerberosConfiguration("client", keytab, true));
//      context.login();
//      return context.getSubject();
//
  }

  static LlamaAMService.Client createClient(boolean secure, String host,
      int port) throws Exception {
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

  static UUID register(final boolean secure, final String llamaHost,
      final int llamaPort, final String clientId, final String callbackHost,
      final int callbackPort) throws Exception {
    return Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<UUID>() {
          @Override
          public UUID run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            TLlamaAMRegisterRequest req = new TLlamaAMRegisterRequest();
            req.setVersion(TLlamaServiceVersion.V1);
            req.setClient_id(clientId);
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
        });
  }

  static void unregister(final boolean secure, final String llamaHost,
      final int llamaPort, final UUID handle) throws Exception {
    Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            TLlamaAMUnregisterRequest req = new TLlamaAMUnregisterRequest();
            req.setVersion(TLlamaServiceVersion.V1);
            req.setAm_handle(TypeUtils.toTUniqueId(handle));
            TLlamaAMUnregisterResponse res = client.Unregister(req);
            if (res.getStatus().getStatus_code() != TStatusCode.OK) {
              throw new RuntimeException(res.toString());
            }
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

  static UUID reserve(final boolean secure, final String llamaHost,
      final int llamaPort, final UUID handle, final String queue,
      final String[] locations, final int cpus, final int memory,
      final boolean relaxLocality,
      final boolean gang) throws Exception {
    return Subject.doAs(getSubject(secure),
        new PrivilegedExceptionAction<UUID>() {
          @Override
          public UUID run() throws Exception {
            LlamaAMService.Client client = createClient(secure, llamaHost,
                llamaPort);
            TLlamaAMReservationRequest req = new TLlamaAMReservationRequest();
            req.setVersion(TLlamaServiceVersion.V1);
            req.setAm_handle(TypeUtils.toTUniqueId(handle));
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
        });
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
            TLlamaAMReleaseRequest req = new TLlamaAMReleaseRequest();
            req.setVersion(TLlamaServiceVersion.V1);
            req.setAm_handle(TypeUtils.toTUniqueId(handle));
            req.setReservation_id(TypeUtils.toTUniqueId(reservation));
            TLlamaAMReleaseResponse res = client.Release(req);
            if (res.getStatus().getStatus_code() != TStatusCode.OK) {
              throw new RuntimeException(res.toString());
            }
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
            System.setProperty(LlamaClientCallback.PORT_KEY,
                Integer.toString(port));
            new LlamaClientCallbackMain().run(new String[0]);
            return null;
          }
        });
  }

}
