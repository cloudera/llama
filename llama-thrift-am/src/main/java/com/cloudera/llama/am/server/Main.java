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
package com.cloudera.llama.am.server;

import com.cloudera.llama.am.server.thrift.LlamaAMThriftServer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.util.VersionInfo;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class Main {
  static final String TEST_LLAMA_JVM_EXIT_SYS_PROP = 
      "test.llama.disable.jvm.exit";

  public static final String SERVER_CLASS_KEY = "llama.am.server.class";
  
  public static final String CONF_DIR_SYS_PROP = "llama.am.server.conf.dir";
  public static final String LOG_DIR_SYS_PROP = "llama.am.server.log.dir";

  public static class Service {

    static void verifyRequiredSysProps() {
      verifySystemPropertyDir(CONF_DIR_SYS_PROP, true);
      verifySystemPropertyDir(LOG_DIR_SYS_PROP, false);
    }
    
    public static void main(String[] args) throws Exception {
      verifyRequiredSysProps();
      Main.main(args);
    }

    private static String verifySystemPropertyDir(String name,
        boolean mustExist) {
      String dir = System.getProperty(name);
      if (dir == null) {
        throw new RuntimeException("Undefined Java System Property '" + name +
            "'");
      }
      if (mustExist && !new File(dir).exists()) {
        throw new RuntimeException("Directory '" + dir + "' does not exist");
      }
      return dir;
    }
  }

  private static final String BUILD_INFO_PROPERTIES = 
    "llama-build-info.properties";
  private static final String LOG4J_PROPERTIES = "llama-log4j.properties";
  private static final String SITE_XML = "llama-site.xml";
  
  private static Logger LOG;
    
  public static void main(String[] args) throws Exception {
    Main main = new Main();
    int exit = main.run(args);
    if (!System.getProperty(TEST_LLAMA_JVM_EXIT_SYS_PROP,
        "false").equals("true")) {
      System.exit(exit);
    }
  }

  private CountDownLatch latch = new CountDownLatch(1);

  //Used for testing only
  void releaseLatch() {
    latch.countDown();
  }
  
  public int run(String[] args) throws Exception {
    String confDir = System.getProperty(CONF_DIR_SYS_PROP);
    initLogging(confDir);
    logServerInfo();

    LOG.info("Configuration directory: {}", confDir);
    Configuration llamaConf = loadConfiguration(confDir);
    Class<? extends AbstractServer> klass = 
      llamaConf.getClass(SERVER_CLASS_KEY, LlamaAMThriftServer.class, 
          AbstractServer.class);
    LOG.info("Server: {}", klass.getName());
    LOG.info("-----------------------------------------------------------------");
    AbstractServer server = ReflectionUtils.newInstance(klass, llamaConf);

    addShutdownHook(server);

    try {
      server.start();
      latch.await();
      server.stop();
    } catch (Exception ex) {
      LOG.error("Server error: {}", ex.toString(), ex);
      server.stop();
      return 1;
    }
    return server.getExitCode();
  }

  private static void initLogging(String confDir) {
    boolean fromClasspath = true;
    File log4jConf = new File(confDir, LOG4J_PROPERTIES).getAbsoluteFile();
    if (log4jConf.exists()) {
      PropertyConfigurator.configureAndWatch(log4jConf.getPath(), 1000);
      fromClasspath = false;
    } else {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      URL log4jUrl = cl.getResource(LOG4J_PROPERTIES);
      PropertyConfigurator.configure(log4jUrl);
    }
    LOG = LoggerFactory.getLogger(Main.class);
    LOG.debug("Llama log starting");
    if (fromClasspath) {
      LOG.warn("Log4j configuration file '{}' not found", LOG4J_PROPERTIES);
      LOG.warn("Logging with INFO level to standard output");
    }
  }

  private static void logServerInfo() {
    Properties props = new Properties();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    InputStream is = cl.getResourceAsStream(BUILD_INFO_PROPERTIES);
    if (is != null) {
      try {
        props.load(is);
        is.close();
      } catch (Exception ex) {
        LOG.warn("Could not read '{}' from classpath: {}", new Object[] {
          BUILD_INFO_PROPERTIES, ex.toString(), ex});
      }
    }
    LOG.info("-----------------------------------------------------------------");
    LOG.info("  Java runtime version : {}",
        System.getProperty("java.runtime.version"));
    LOG.info("  Llama version        : {}", props.getProperty("llama.version", 
      "?"));
    LOG.info("  Llama built date     : {}", props.getProperty("llama.built.date", 
      "?"));
    LOG.info("  Llama built by       : {}", props.getProperty("llama.built.by", 
      "?"));
    LOG.info("  Llama revision       : {}", props.getProperty("llama.revision", 
      "?"));
    LOG.info("  Hadoop version       : {}", VersionInfo.getVersion());
    LOG.info("-----------------------------------------------------------------");
  }

  private static Configuration loadConfiguration(String confDir) {
    Configuration llamaConf = new Configuration(false);
    File file = new File(confDir, SITE_XML);
    if (!file.exists()) {
      LOG.warn("Llama configuration file '{}' not found in '{}'", SITE_XML, 
          confDir);
    } else {
      llamaConf.addResource(new Path(file.getAbsolutePath()));
    }
    return llamaConf;
  }
  
  private static void addShutdownHook(final AbstractServer server) {
    if (!System.getProperty(TEST_LLAMA_JVM_EXIT_SYS_PROP, 
        "false").equals("true")) {
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          server.shutdown(0);
        }
      });
    }
  }
}
