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

import org.apache.hadoop.conf.Configuration;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.File;
import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Security {

  static class KerberosConfiguration extends 
      javax.security.auth.login.Configuration {
    private String principal;
    private String keytab;
    private boolean isInitiator;

    KerberosConfiguration(String principal, File keytab, 
        boolean client) {
      this.principal = principal;
      this.keytab = keytab.getAbsolutePath();
      this.isInitiator = client;
    }

    private static String getKrb5LoginModuleName() {
      return System.getProperty("java.vendor").contains("IBM")
             ? "com.ibm.security.auth.module.Krb5LoginModule"
             : "com.sun.security.auth.module.Krb5LoginModule";
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
      Map<String, String> options = new HashMap<String, String>();
      options.put("keyTab", keytab);
      options.put("principal", principal);
      options.put("useKeyTab", "true");
      options.put("storeKey", "true");
      options.put("doNotPrompt", "true");
      options.put("useTicketCache", "true");
      options.put("renewTGT", "true");
      options.put("refreshKrb5Config", "true");
      options.put("isInitiator", Boolean.toString(isInitiator));
      String ticketCache = System.getenv("KRB5CCNAME");
      if (ticketCache != null) {
        options.put("ticketCache", ticketCache);
      }
      options.put("debug", System.getProperty("sun.security.krb5.debug=true", 
          "false"));

      return new AppConfigurationEntry[]{
          new AppConfigurationEntry(getKrb5LoginModuleName(),
              AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
              options)};
    }
  }

  public static boolean isSecure(Configuration conf) {
    return conf.getBoolean(ServerConfiguration.SECURITY_ENABLED_KEY, 
        ServerConfiguration.SECURITY_ENABLED_DEFAULT);
  }
  
  private static final Map<Subject, LoginContext> SUBJECT_LOGIN_CTX_MAP = 
      new ConcurrentHashMap<Subject, LoginContext>();
  
  static Subject loginSubject(Configuration conf, boolean isClient) 
      throws Exception {
    Subject subject;
    if (isSecure(conf)) {
      String principalName = conf.get(
          ServerConfiguration.SERVER_PRINCIPAL_NAME_KEY,
          ServerConfiguration.SERVER_PRINCIPAL_NAME_DEFAULT);
      String keytab = conf.get(ServerConfiguration.KEYTAB_FILE_KEY,
          ServerConfiguration.KEYTAB_FILE_DEFAULT);
      if (!(keytab.charAt(0) == '/')) {
        String confDir = conf.get(ServerConfiguration.CONFIG_DIR_KEY);
        keytab = new File(confDir, keytab).getAbsolutePath();
      }
      File keytabFile = new File(keytab);
      Set<Principal> principals = new HashSet<Principal>();
      principals.add(new KerberosPrincipal(principalName));
      subject = new Subject(false, principals, new HashSet<Object>(),
          new HashSet<Object>());
      LoginContext context = new LoginContext("", subject, null,
          new KerberosConfiguration(principalName, keytabFile, isClient));
      context.login();
      subject = context.getSubject();
      SUBJECT_LOGIN_CTX_MAP.put(subject, context);
    } else {
      subject = new Subject();
    }
    return subject;
  }
 
  public static Subject loginServerSubject(Configuration conf) 
    throws Exception {
    return loginSubject(conf, false);
  }

  public static Subject loginClientSubject(Configuration conf)
      throws Exception {
    return loginSubject(conf, true);
  }

  public static void logout(Subject subject) {
    LoginContext loginContext = SUBJECT_LOGIN_CTX_MAP.get(subject);
    if (loginContext != null) {
      try {
        loginContext.logout();
      } catch (LoginException ex) {
        //TODO LOG
      }
    }
  }
  
}
