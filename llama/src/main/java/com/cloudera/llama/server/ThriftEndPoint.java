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

import com.cloudera.llama.am.impl.FastFormat;
import org.apache.hadoop.net.NetUtils;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSaslServerTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportFactory;

import javax.security.sasl.Sasl;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class ThriftEndPoint {

  public static TTransport createClientTransport(ServerConfiguration conf,
      String host, int port) throws Exception {
    int timeout = conf.getTransportTimeOut();

    TTransport tTransport = new TSocket(host, port, timeout);
    if (Security.isSecure(conf)) {
      String serviceName = conf.getNotificationPrincipalName();
      Map<String, String> saslProperties = new HashMap<String, String>();
      saslProperties.put(Sasl.QOP, "auth-conf");
      tTransport = new TSaslClientTransport("GSSAPI", null, serviceName, host,
          saslProperties, null, tTransport);
    }
    return tTransport;
  }

  public static TServerSocket createTServerSocket(ServerConfiguration conf)
      throws Exception {
    String strAddress = conf.getThriftAddress();
    int timeout = conf.getTransportTimeOut();
    int defaultPort = conf.getThriftDefaultPort();
    InetSocketAddress address = NetUtils.createSocketAddr(strAddress,
        defaultPort);
    return new TServerSocket(address, timeout);
  }

  public static TTransportFactory createTTransportFactory(
      ServerConfiguration conf) {
    TTransportFactory factory;
    if (Security.isSecure(conf)) {
      TSaslServerTransport.Factory saslFactory = null;
      Map<String, String> saslProperties = new HashMap<String, String>();
      saslProperties.put(Sasl.QOP, "auth-conf");
      String principalName = Security.resolveLlamaPrincipalName(conf);
      String declarePrincipalHost = null;
      int i = principalName.indexOf("/");
      if (i > -1) {
        declarePrincipalHost = principalName.substring(i + 1);
        principalName = principalName.substring(0, i);
      }
      String principalHost = getServerAddress(conf);
      if (!principalHost.equals(declarePrincipalHost)) {
        throw new RuntimeException(FastFormat.format(
            "Server address configured with '{}', " +
                "Kerberos service hostname configured with '{}'",
            principalHost, declarePrincipalHost));
      }
      saslFactory = new TSaslServerTransport.Factory();
      saslFactory.addServerDefinition("GSSAPI", principalName, principalHost,
          saslProperties, new GssCallback());
      factory = saslFactory;
    } else {
      factory = new TTransportFactory();
    }
    return factory;
  }

  public static String getServerAddress(ServerConfiguration conf) {
    String strAddress = conf.getHttpAddress();
    int defaultPort = conf.getHttpDefaultPort();
    InetSocketAddress address = NetUtils.createSocketAddr(strAddress,
        defaultPort);
    return address.getHostName();
  }

  public static int getServerPort(ServerConfiguration conf) {
    String strAddress = conf.getThriftAddress();
    int defaultPort = conf.getThriftDefaultPort();
    InetSocketAddress address = NetUtils.createSocketAddr(strAddress,
        defaultPort);
    return address.getPort();
  }

}
