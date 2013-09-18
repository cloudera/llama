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

import com.cloudera.llama.am.impl.ParamChecker;
import com.cloudera.llama.minikdc.MiniKdc;
import com.cloudera.llama.server.Security;
import com.cloudera.llama.server.ServerConfiguration;
import com.cloudera.llama.server.TestAbstractMain;
import com.cloudera.llama.thrift.TLlamaAMRegisterRequest;
import com.cloudera.llama.thrift.TLlamaAMRegisterResponse;
import com.cloudera.llama.thrift.TLlamaServiceVersion;
import com.cloudera.llama.thrift.TNetworkAddress;
import com.cloudera.llama.thrift.TStatusCode;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.sasl.Sasl;
import java.io.File;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestSecureLlamaAMThriftServer extends TestLlamaAMThriftServer {
  private ServerConfiguration amConf = new AMServerConfiguration(
      new Configuration(false));

  private MiniKdc miniKdc;

  @Before
  public void startKdc() throws Exception {
    miniKdc = new MiniKdc(MiniKdc.createConf(),
        new File(TestAbstractMain.createTestDir()));
    miniKdc.start();
  }

  @After
  public void stopKdc() {
    miniKdc.stop();
  }

  @Override
  protected Configuration createCallbackConfiguration() throws Exception {
    ServerConfiguration cConf = new NotificationServerConfiguration();
    Configuration conf = super.createCallbackConfiguration();
    String confDir = conf.get(ServerConfiguration.CONFIG_DIR_KEY);
    ParamChecker.notEmpty(confDir, "missing confDir");

    conf.setBoolean(cConf.getPropertyName(
        ServerConfiguration.SECURITY_ENABLED_KEY), true);
    File keytab = new File(confDir, "notification.keytab");
    miniKdc.createPrincipal(keytab, "notification/localhost");
    conf.set(cConf.getPropertyName(ServerConfiguration.KEYTAB_FILE_KEY),
        keytab.getAbsolutePath());
    conf.set(cConf.getPropertyName(ServerConfiguration.SERVER_PRINCIPAL_NAME_KEY),
        "notification/localhost");
    conf.set(cConf.getPropertyName(ServerConfiguration.SERVER_ADDRESS_KEY),
        "localhost:0");
    return conf;
  }

  @Override
  protected Configuration createLlamaConfiguration() throws Exception {
    Configuration conf = super.createLlamaConfiguration();
    String confDir = conf.get(ServerConfiguration.CONFIG_DIR_KEY);
    ParamChecker.notEmpty(confDir, "missing confDir");
    conf.setBoolean(amConf.getPropertyName(
        ServerConfiguration.SECURITY_ENABLED_KEY), true);
    File keytab = new File(confDir, "llama.keytab");
    miniKdc.createPrincipal(keytab, "llama/localhost");
    conf.set(amConf.getPropertyName(ServerConfiguration.KEYTAB_FILE_KEY),
        keytab.getAbsolutePath());
    conf.set(amConf.getPropertyName(ServerConfiguration.SERVER_PRINCIPAL_NAME_KEY),
        "llama/localhost");
    conf.set(amConf.getPropertyName(ServerConfiguration.SERVER_ADDRESS_KEY),
        "localhost:0");
    conf.set(amConf.getPropertyName(
        ServerConfiguration.NOTIFICATION_PRINCIPAL_NAME_KEY), "notification");
    return conf;
  }

  protected com.cloudera.llama.thrift.LlamaAMService.Client createClient(
      LlamaAMServer server)
      throws Exception {
    TTransport transport = new TSocket(server.getAddressHost(),
        server.getAddressPort());
    Map<String, String> saslProperties = new HashMap<String, String>();
    saslProperties.put(Sasl.QOP, "auth-conf");
    transport = new TSaslClientTransport("GSSAPI", null, "llama",
        server.getAddressHost(), saslProperties, null, transport);
    transport.open();
    TProtocol protocol = new TBinaryProtocol(transport);
    return new com.cloudera.llama.thrift.LlamaAMService.Client(protocol);
  }

  @Override
  protected Subject getClientSubject() throws Exception {
    File keytab = new File(TestAbstractMain.createTestDir(), "client.keytab");
    miniKdc.createPrincipal(keytab, "client");
    Set<Principal> principals = new HashSet<Principal>();
    principals.add(new KerberosPrincipal("client"));
    Subject subject = new Subject(false, principals, new HashSet<Object>(),
        new HashSet<Object>());
    LoginContext context = new LoginContext("", subject, null,
        new Security.KerberosConfiguration("client", keytab, true));
    context.login();
    return context.getSubject();
  }

  @Test
  @Override
  public void testRegister() throws Exception {
    super.testRegister();
  }

  @Test(expected = TTransportException.class)
  public void testUnauthorized() throws Throwable {
    final LlamaAMServer server = new LlamaAMServer();
    try {
      Configuration conf = createLlamaConfiguration();
      conf.set("hadoop.security.group.mapping", MockGroupMapping.class.getName());
      conf.set("llama.am.server.thrift.client.acl", "nobody");
      server.setConf(conf);
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client = createClient(server);


          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id("c1");
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname("localhost");
          tAddress.setPort(0);
          trReq.setNotification_callback_service(tAddress);

          //register
          client.Register(trReq);

          return null;
        }
      });
    } catch (PrivilegedActionException ex) {
      throw ex.getCause();
    } finally {
      server.stop();
    }
  }

  @Test
  public void testAuthorized() throws Exception {
    final LlamaAMServer server = new LlamaAMServer();
    try {
      Configuration conf = createLlamaConfiguration();
      conf.set("hadoop.security.group.mapping", MockGroupMapping.class.getName());
      conf.set("llama.am.server.thrift.client.acl", "nobody group");
      server.setConf(conf);
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client = createClient(server);


          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id("c1");
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname("localhost");
          tAddress.setPort(0);
          trReq.setNotification_callback_service(tAddress);

          //register
          TLlamaAMRegisterResponse trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK,
              trRes.getStatus().getStatus_code());

          return null;
        }
      });
    } finally {
      server.stop();
    }
  }

  @Test
  public void testAllAuthorized() throws Exception {
    final LlamaAMServer server = new LlamaAMServer();
    try {
      Configuration conf = createLlamaConfiguration();
      conf.set("hadoop.security.group.mapping", MockGroupMapping.class.getName());
      conf.set("llama.am.server.thrift.client.acl", "*");
      server.setConf(conf);
      server.start();

      Subject.doAs(getClientSubject(), new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          com.cloudera.llama.thrift.LlamaAMService.Client client = createClient(server);


          TLlamaAMRegisterRequest trReq = new TLlamaAMRegisterRequest();
          trReq.setVersion(TLlamaServiceVersion.V1);
          trReq.setClient_id("c1");
          TNetworkAddress tAddress = new TNetworkAddress();
          tAddress.setHostname("localhost");
          tAddress.setPort(0);
          trReq.setNotification_callback_service(tAddress);

          //register
          TLlamaAMRegisterResponse trRes = client.Register(trReq);
          Assert.assertEquals(TStatusCode.OK,
              trRes.getStatus().getStatus_code());

          return null;
        }
      });
    } finally {
      server.stop();
    }
  }

}
