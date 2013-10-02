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

import com.cloudera.llama.thrift.LlamaNotificationService;
import com.cloudera.llama.thrift.TLlamaAMNotificationRequest;
import com.cloudera.llama.thrift.TLlamaAMNotificationResponse;
import com.cloudera.llama.thrift.TLlamaNMNotificationRequest;
import com.cloudera.llama.thrift.TLlamaNMNotificationResponse;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlamaClientCallback extends
    ThriftServer<LlamaNotificationService.Processor> {
  public static final String PORT_KEY = LlamaClientCallback.class.getName() +
      ".port";
  private static final Logger LOG =
      LoggerFactory.getLogger(LlamaClientCallback.class);

  public static class ClientCallbackServerConfiguration
      extends ServerConfiguration {

    public ClientCallbackServerConfiguration() {
      super("cc");
    }

    @Override
    public int getThriftDefaultPort() {
      return Integer.parseInt(System.getProperty(PORT_KEY, "0"));
    }

    @Override
    public int getHttpDefaultPort() {
      return 0;
    }
  }

  public LlamaClientCallback() {
    super("LlamaClientCallback", ClientCallbackServerConfiguration.class);
  }

  public static class LNServiceImpl implements LlamaNotificationService.Iface {

    @Override
    public TLlamaAMNotificationResponse AMNotification(
        TLlamaAMNotificationRequest request) throws TException {
      LOG.info(request.toString());
      return new TLlamaAMNotificationResponse().setStatus(TypeUtils.OK);
    }

    @Override
    public TLlamaNMNotificationResponse NMNotification(
        TLlamaNMNotificationRequest request) throws TException {
      LOG.info(request.toString());
      return new TLlamaNMNotificationResponse().setStatus(TypeUtils.OK);
    }
  }

  @Override
  protected LlamaNotificationService.Processor createServiceProcessor() {
    Class<? extends LlamaNotificationService.Iface> klass = getConf().getClass(
        "cc.handler.class", LNServiceImpl.class,
        LlamaNotificationService.Iface.class);
    LlamaNotificationService.Iface handler =
        ReflectionUtils.newInstance(klass, getConf());
    return new LlamaNotificationService.Processor<LlamaNotificationService
        .Iface>(handler);
  }

  @Override
  protected void startService() {
  }

  @Override
  protected void stopService() {
  }

}
