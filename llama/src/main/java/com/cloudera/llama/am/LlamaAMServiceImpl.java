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

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.server.ClientNotificationService;
import com.cloudera.llama.server.NodeMapper;
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
import com.cloudera.llama.thrift.TNetworkAddress;
import com.cloudera.llama.util.UUID;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LlamaAMServiceImpl implements LlamaAMService.Iface {
  private static final Logger LOG = LoggerFactory.getLogger(
      LlamaAMServiceImpl.class);

  private final LlamaAM llamaAM;
  private final NodeMapper nodeMapper;
  private final ClientNotificationService clientNotificationService;

  @SuppressWarnings("unchecked")
  public LlamaAMServiceImpl(LlamaAM llamaAM, NodeMapper nodeMapper,
      ClientNotificationService clientNotificationService) {
    this.llamaAM = llamaAM;
    this.nodeMapper = nodeMapper;
    this.clientNotificationService = clientNotificationService;
    llamaAM.addListener(clientNotificationService);
  }

  @Override
  public TLlamaAMRegisterResponse Register(TLlamaAMRegisterRequest request)
      throws TException {
    TLlamaAMRegisterResponse response = new TLlamaAMRegisterResponse();
    try {
      UUID clientId = TypeUtils.toUUID(request.getClient_id());
      TNetworkAddress tAddress = request.getNotification_callback_service();
      UUID handle = clientNotificationService.register(clientId,
          tAddress.getHostname(), tAddress.getPort());
      response.setStatus(TypeUtils.OK);
      response.setAm_handle(TypeUtils.toTUniqueId(handle));
    } catch (Throwable ex) {
      LOG.warn("Register() error: {}", ex.toString(), ex);
      response.setStatus(TypeUtils.createError(ex));
    }
    return response;
  }

  @Override
  public TLlamaAMUnregisterResponse Unregister(
      TLlamaAMUnregisterRequest request) throws TException {
    TLlamaAMUnregisterResponse response = new TLlamaAMUnregisterResponse();
    try {
      UUID handle = TypeUtils.toUUID(request.getAm_handle());
      if (!clientNotificationService.unregister(handle)) {
        LOG.warn("Unregister() unknown handle '{}'", handle);
      }
      response.setStatus(TypeUtils.OK);
    } catch (Exception ex) {
      LOG.warn("Unregister() internal error: {}", ex.toString(), ex);
      response.setStatus(TypeUtils.createError(ex));
    }
    return response;
  }

  @Override
  public TLlamaAMReservationResponse Reserve(TLlamaAMReservationRequest request)
      throws TException {
    TLlamaAMReservationResponse response = new TLlamaAMReservationResponse();
    try {
      UUID handle = TypeUtils.toUUID(request.getAm_handle());
      clientNotificationService.validateHandle(handle);
      Reservation reservation = TypeUtils.toReservation(request, nodeMapper);
      UUID reservationId = llamaAM.reserve(reservation).getReservationId();
      response.setReservation_id(TypeUtils.toTUniqueId(reservationId));
      response.setStatus(TypeUtils.OK);
    } catch (Throwable ex) {
      LOG.warn("Reserve() error: {}", ex.toString(), ex);
      response.setStatus(TypeUtils.createError(ex));
    }
    return response;
  }

  @Override
  public TLlamaAMReleaseResponse Release(TLlamaAMReleaseRequest request)
      throws TException {
    TLlamaAMReleaseResponse response = new TLlamaAMReleaseResponse();
    try {
      UUID handle = TypeUtils.toUUID(request.getAm_handle());
      clientNotificationService.validateHandle(handle);
      UUID reservationId = TypeUtils.toUUID(request.getReservation_id());
      llamaAM.releaseReservation(handle, reservationId, false);
      response.setStatus(TypeUtils.OK);
    } catch (Throwable ex) {
      LOG.warn("Release() error: {}", ex.toString(), ex);
      response.setStatus(TypeUtils.createError(ex));
    }
    return response;
  }

  @Override
  public TLlamaAMGetNodesResponse GetNodes(TLlamaAMGetNodesRequest request)
      throws TException {
    TLlamaAMGetNodesResponse response = new TLlamaAMGetNodesResponse();
    try {
      UUID handle = TypeUtils.toUUID(request.getAm_handle());
      clientNotificationService.validateHandle(handle);
      List<String> nodes = nodeMapper.getDataNodes(llamaAM.getNodes());
      response.setNodes(nodes);
      response.setStatus(TypeUtils.OK);
    } catch (Throwable ex) {
      LOG.warn("GetNodes() error: {}", ex.toString(), ex);
      response.setStatus(TypeUtils.createError(ex));
    }
    return response;
  }

}
