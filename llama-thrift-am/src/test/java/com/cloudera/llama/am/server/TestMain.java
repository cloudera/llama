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

import com.cloudera.llama.am.LlamaAM;
import com.cloudera.llama.am.mock.MockRMLlamaAMConnector;
import com.cloudera.llama.am.server.thrift.ServerConfiguration;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Writer;
import java.util.UUID;

public class TestMain {
  public static final String LLAMA_BUILD_DIR = "test.llama.build.dir";
  
  public static String createTestDir() {
    File dir = new File(System.getProperty(LLAMA_BUILD_DIR, "target"));
    dir = new File(dir, UUID.randomUUID().toString()).getAbsoluteFile();
    dir.mkdirs();
    return dir.getAbsolutePath();
  }

  @Before
  public void beforeTest() {
    System.setProperty(Main.TEST_LLAMA_JVM_EXIT_SYS_PROP, "true");
    System.getProperties().remove(Main.CONF_DIR_SYS_PROP);
    System.getProperties().remove(Main.LOG_DIR_SYS_PROP);
  }

  @After
  public void afterTest() {
    System.getProperties().remove(Main.TEST_LLAMA_JVM_EXIT_SYS_PROP);
    System.getProperties().remove(Main.CONF_DIR_SYS_PROP);
    System.getProperties().remove(Main.LOG_DIR_SYS_PROP);
  }

  private void createMainConf(String confDir, Configuration conf) throws Exception{
    System.setProperty(Main.CONF_DIR_SYS_PROP, confDir);
    conf.setIfUnset(LlamaAM.RM_CONNECTOR_CLASS_KEY, MockRMLlamaAMConnector.class.getName());
    conf.set(ServerConfiguration.SERVER_ADDRESS_KEY, "localhost:0");
    conf.set(ServerConfiguration.HTTP_JMX_ADDRESS_KEY, "localhost:0");
    Writer writer = new FileWriter(new File(confDir, "llama-site.xml"));
    conf.writeXml(writer);
    writer.close();
  }
  
  @Test
  public void testMainOK1() throws Exception {
    String testDir = createTestDir();
    createMainConf(testDir, new Configuration(false));
    final Main main = new Main();
    main.releaseRunningLatch();
    Assert.assertEquals(0, main.run(null));    
    main.waitStopLach();
  }

  @Test
  public void testMainOK2() throws Exception {
    String testDir = createTestDir();
    createMainConf(testDir, new Configuration(false));
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    InputStream is = cl.getResourceAsStream("log4j.properties");
    FileUtils.copyInputStreamToFile(is, new File(testDir,
        "llama-log4j.properties"));
    System.setProperty(Main.CONF_DIR_SYS_PROP, testDir);
    System.setProperty(Main.LOG_DIR_SYS_PROP, testDir);
    final Main main = new Main();
    main.releaseRunningLatch();
    Assert.assertEquals(0, main.run(null));
    main.waitStopLach();
  }

  @Test
  public void testMainError() throws Exception {
    String testDir = createTestDir();
    Configuration conf = new Configuration(false);
    conf.set(LlamaAM.RM_CONNECTOR_CLASS_KEY, "x");
    createMainConf(testDir, conf);
    System.setProperty(Main.CONF_DIR_SYS_PROP, testDir);
    System.setProperty(Main.LOG_DIR_SYS_PROP, testDir);
    Assert.assertNotSame(0, new Main().run(null));
  }

  @Test(expected = RuntimeException.class)
  public void testServiceError1() throws Exception {
    Main.Service.verifyRequiredSysProps();
  }

  @Test(expected = RuntimeException.class)
  public void testServiceError2() throws Exception {
    String testDir = createTestDir();
    System.setProperty(Main.CONF_DIR_SYS_PROP, testDir);
    Main.Service.verifyRequiredSysProps();
  }
  
  @Test(expected = RuntimeException.class)
  public void testServiceError3() throws Exception {
    String testDir = createTestDir();
    System.setProperty(Main.CONF_DIR_SYS_PROP, UUID.randomUUID().toString());
    System.setProperty(Main.LOG_DIR_SYS_PROP, testDir);
    Main.Service.verifyRequiredSysProps();
  }

  @Test
  public void testServiceOK1() throws Exception {
    String testDir = createTestDir();
    createMainConf(testDir, new Configuration(false));
    System.setProperty(Main.CONF_DIR_SYS_PROP, testDir);
    System.setProperty(Main.LOG_DIR_SYS_PROP, testDir);
    Main.Service.verifyRequiredSysProps();
  }

}
