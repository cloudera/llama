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
import org.apache.hadoop.conf.Configuration;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.sasl.Sasl;
import java.io.File;
import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestSecureLlamaAMThriftServer extends TestLlamaAMThriftServer {
  private ServerConfiguration amConf =
      new ServerConfiguration("am", new Configuration(false));

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
    ServerConfiguration cConf = new ServerConfiguration("client",
        new Configuration(false));
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
}
