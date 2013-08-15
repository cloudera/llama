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

import com.cloudera.llama.am.LlamaAM;
import com.cloudera.llama.am.yarn.YarnRMLlamaAMConnector;
import com.cloudera.llama.thrift.LlamaAMService;
import org.apache.hadoop.util.ReflectionUtils;

public class LlamaAMThriftServer extends 
    ThriftServer<LlamaAMService.Processor> {
  private LlamaAM llamaAm;
  private ClientNotificationService clientNotificationService;
  private ClientNotifier clientNotifier;
  private NodeMapper nodeMapper;

  public LlamaAMThriftServer() {
    super("LlamaAM");
  }

  @Override
  protected void startService() {
    try {
      Security.loginToHadoop(getConf());
      Class<? extends NodeMapper> klass = getConf().getClass(
          ServerConfiguration.NODE_NAME_MAPPING_CLASS_KEY,
          ServerConfiguration.NODE_NAME_MAPPING_CLASS_DEFAULT,
          NodeMapper.class);
      nodeMapper = ReflectionUtils.newInstance(klass, getConf());
      clientNotificationService = new ClientNotificationService(getConf());
      clientNotifier = new ClientNotifier(getConf(), nodeMapper, 
          clientNotificationService);

      getConf().set(YarnRMLlamaAMConnector.ADVERTISED_HOSTNAME_KEY, 
          ThriftEndPoint.getServerAddress(getConf()));
      getConf().set(YarnRMLlamaAMConnector.ADVERTISED_TRACKING_URL_KEY, 
          YarnRMLlamaAMConnector.NOT_AVAILABLE_VALUE);
      llamaAm = LlamaAM.create(getConf());      
      clientNotifier.start();
      llamaAm.start();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void stopService() {
    llamaAm.stop();
    clientNotifier.stop();
  }

  @Override
  protected LlamaAMService.Processor createServiceProcessor() {
    LlamaAMService.Iface handler = new LlamaAMServiceImpl(llamaAm, nodeMapper,
        clientNotificationService, clientNotifier);
    return new LlamaAMService.Processor<LlamaAMService.Iface>(handler);
  }

}
