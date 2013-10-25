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

import com.cloudera.llama.am.api.LlamaAMObserver;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.server.ClientInfo;
import com.cloudera.llama.server.ClientNotificationService;
import com.cloudera.llama.util.UUID;
import com.cloudera.llama.util.VersionInfo;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.module.SimpleModule;
import org.codehaus.jackson.map.ser.SerializerBase;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RestData implements LlamaAMObserver,
    ClientNotificationService.Listener {

  private static final Map<String, String> VERSION_INFO =
      new LinkedHashMap<String, String>();
  
  static {
    VERSION_INFO.put("llama.version", VersionInfo.getVersion());
    VERSION_INFO.put("llama.built.date", VersionInfo.getBuiltDate());
    VERSION_INFO.put("llama.built.by", VersionInfo.getBuiltBy());
    VERSION_INFO.put("llama.scm.uri", VersionInfo.getSCMURI());
    VERSION_INFO.put("llama.scm.revision", VersionInfo.getSCMRevision());
    VERSION_INFO.put("llama.source.md5", VersionInfo.getSourceMD5());
    VERSION_INFO.put("llama.hadoop.version", VersionInfo.getHadoopVersion());
  }

  public static final String REST_VERSION_KEY = "llamaRestJsonVersion";
  public static final String REST_VERSION_VALUE = "1.0.0";

  private static final String VERSION_INFO_KEY = "llamaVersionInfo";
  private static final String RESERVATIONS_COUNT_KEY = "reservationsCount";
  private static final String QUEUES_SUMMARY_KEY = "queuesSummary";
  private static final String CLIENTS_SUMMARY_KEY = "clientsSummary";
  private static final String NODES_SUMMARY_KEY = "nodesSummary";

  private static final String SUMMARY_DATA = "summaryData";
  private static final String RESERVATION_DATA = "reservationData";
  private static final String QUEUE_DATA = "queueData";
  private static final String HANDLE_DATA = "handleData";
  private static final String NODE_DATA = "nodeData";

  private final ObjectWriter jsonWriter;
  private final ReadWriteLock lock;
  private final Map<UUID, PlacedReservation> reservationsMap;
  private final Map<UUID, List<PlacedReservation>> clientReservationsMap;
  private final Map<String, List<PlacedReservation>> queueReservationsMap;
  private final Map<String, List<PlacedResource>> nodeResourcesMap;
  private final Map<UUID, ClientInfo> clientInfoMap;

  public static ObjectWriter createJsonWriter() {
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule("LlamaModule",
        new Version(1, 0, 0, null));
    module.addSerializer(new UUIDSerializer());
    module.addSerializer(new ClientInfoImplSerializer());
    module.addSerializer(new PlacedReservationSerializer());
    module.addSerializer(new PlacedResourceSerializer());
    mapper.registerModule(module);
    return mapper.defaultPrettyPrintingWriter();
  }

  public RestData() {
    jsonWriter = createJsonWriter();
    lock = new ReentrantReadWriteLock();
    reservationsMap = new LinkedHashMap<UUID, PlacedReservation>();
    clientReservationsMap = new LinkedHashMap<UUID, List<PlacedReservation>>();
    queueReservationsMap = new TreeMap<String, List<PlacedReservation>>();
    nodeResourcesMap = new TreeMap<String, List<PlacedResource>>();
    clientInfoMap = new LinkedHashMap<UUID, ClientInfo>();
  }

  public void onRegister(ClientInfo clientInfo) {
    clientInfoMap.put(clientInfo.getHandle(), clientInfo);
  }

  public void onUnregister(ClientInfo clientInfo) {
    clientInfoMap.remove(clientInfo.getHandle());

  }

  @Override
  public void observe(PlacedReservation reservation) {
    lock.writeLock().lock();
    try {
      switch (reservation.getStatus()) {
        case PENDING:
          add(reservation);
          break;
        case PARTIAL:
        case BACKED_OFF:
        case ALLOCATED:
          update(reservation);
          break;
        case ENDED:
          delete(reservation);
          break;
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private <K,V> void addToMapList(Map<K, List<V>> map, K key, V value) {
    List<V> list = map.get(key);
    if (list == null) {
      list = new ArrayList<V>();
      map.put(key, list);
    }
    list.add(value);
  }

  private void add(PlacedReservation r) {
    reservationsMap.put(r.getReservationId(), r);
    addToMapList(clientReservationsMap, r.getHandle(), r);
    addToMapList(queueReservationsMap, r.getQueue(), r);
    for (PlacedResource resource : r.getResources()) {
      addToMapList(nodeResourcesMap, resource.getLocation(), resource);
    }
  }

  private <K, V> void updateToMapList(Map<K, List<V>> map, K key, V value) {
    List<V> list = map.get(key);
    if (list != null) {
      int index = list.indexOf(value);
      if (index >= 0) {
        list.set(index, value);
      } else {
        //LOG ERROR
      }
    } else {
      //LOG ERROR
    }
  }

  private void updateNodeResourceMapList(Map<String, List<PlacedResource>> map,
      String key, PlacedResource newValue) {
    List<PlacedResource> list = map.get(key);
    if (list != null) {
      int index = list.indexOf(newValue);
      if (index >= 0) {
        PlacedResource oldValue = list.get(index);
        if (newValue.getStatus() != PlacedResource.Status.ALLOCATED ||
            newValue.getActualLocation().equals(oldValue.getLocation())) {
         list.set(index, newValue);
        } else {
          list.remove(index);
          if (list.isEmpty()) {
            map.remove(key);
          }
          addToMapList(map, key, newValue);
        }
      } else {
        //LOG ERROR
      }
    } else {
      //LOG ERROR
    }
  }

  private void update(PlacedReservation r) {
    reservationsMap.put(r.getReservationId(), r);
    updateToMapList(clientReservationsMap, r.getHandle(), r);
    updateToMapList(queueReservationsMap, r.getQueue(), r);
    for (PlacedResource resource : r.getResources()) {
      updateNodeResourceMapList(nodeResourcesMap, resource.getLocation(),
          resource);
    }
  }

  private <K, V> void deleteFromMapList(Map<K, List<V>> map, K key, V value) {
    List<V> list = map.get(key);
    if (list != null) {
      int index = list.indexOf(value);
      if (index >= 0) {
        list.remove(index);
      } else {
        //LOG ERROR
      }
      if (list.isEmpty()) {
        map.remove(key);
      }
    } else {
      //LOG ERROR
    }
  }

  private void delete(PlacedReservation r) {
    reservationsMap.remove(r.getReservationId());
    deleteFromMapList(clientReservationsMap, r.getHandle(), r);
    deleteFromMapList(queueReservationsMap, r.getQueue(), r);
    for (PlacedResource resource : r.getResources()) {
      deleteFromMapList(nodeResourcesMap, resource.getLocation(), resource);
    }
  }

  public static class UUIDSerializer extends
      SerializerBase<UUID> {

    protected UUIDSerializer() {
      super(UUID.class);
    }

    @Override
    public void serialize(UUID value, JsonGenerator jgen,
        SerializerProvider provider)
        throws IOException {
      jgen.writeString(value.toString());
    }

    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        throws JsonMappingException {
      return createSchemaNode("string");
    }
  }

  public static class ClientInfoImplSerializer extends
      SerializerBase<ClientInfoImpl> {

    protected ClientInfoImplSerializer() {
      super(ClientInfoImpl.class);
    }

    @Override
    public void serialize(ClientInfoImpl value, JsonGenerator jgen,
        SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      jgen.writeObjectField("clientId", value.getClientId());
      jgen.writeObjectField("handle", value.getHandle());
      jgen.writeStringField("callbackAddress", value.getCallbackAddress());
      jgen.writeNumberField("reservations", value.getReservations());
      jgen.writeEndObject();
    }

    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        throws JsonMappingException {
      return createSchemaNode("object");
    }
  }

  public static class PlacedReservationSerializer extends
      SerializerBase<PlacedReservation> {

    protected PlacedReservationSerializer() {
      super(PlacedReservation.class);
    }

    @Override
    public void serialize(PlacedReservation value, JsonGenerator jgen,
        SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      jgen.writeObjectField("reservationId", value.getReservationId());
      jgen.writeStringField("placedOn", formatDateTime(value.getPlacedOn()));
      jgen.writeObjectField("handle", value.getHandle());
      jgen.writeStringField("queue", value.getQueue());
      jgen.writeBooleanField("gang", value.isGang());
      jgen.writeStringField("status", value.getStatus().toString());
      jgen.writeObjectField("resources", value.getResources());
      jgen.writeEndObject();
    }

    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        throws JsonMappingException {
      return createSchemaNode("object");
    }
  }

  public static class PlacedResourceSerializer extends
      SerializerBase<PlacedResource> {

    public PlacedResourceSerializer() {
      super(PlacedResource.class);
    }

    @Override
    public void serialize(PlacedResource value, JsonGenerator jgen,
        SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      jgen.writeObjectField("clientResourceId", value.getClientResourceId());
      jgen.writeStringField("location", value.getLocation());
      jgen.writeStringField("locationEnforcement", value.getEnforcement().toString());
      jgen.writeNumberField("cpuVCores", value.getCpuVCores());
      jgen.writeNumberField("memoryMb", value.getMemoryMb());
      jgen.writeObjectField("handle", value.getHandle());
      jgen.writeStringField("queue", value.getQueue());
      jgen.writeObjectField("reservationId", value.getReservationId());
      jgen.writeStringField("rmResourceId", value.getRmResourceId());
      jgen.writeStringField("actualLocation", value.getActualLocation());
      jgen.writeNumberField("actualCpuVCores", value.getActualCpuVCores());
      jgen.writeNumberField("actualMemoryMb", value.getActualMemoryMb());
      jgen.writeStringField("status", value.getStatus().toString());
      jgen.writeEndObject();
    }

    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        throws JsonMappingException {
      return createSchemaNode("object");
    }
  }

  public static class NotFoundException extends Exception {
    public NotFoundException() {
    }
  }

  @SuppressWarnings("unchecked")
  private void writeAsJson(String payloadType, Object obj, Writer out)
      throws IOException, NotFoundException {
    if (obj != null) {
      Map map = new LinkedHashMap();
      map.put(REST_VERSION_KEY, REST_VERSION_VALUE);
      map.put(payloadType, obj);
      jsonWriter.writeValue(out, map);
    } else {
      throw new NotFoundException();
    }
  }

  @SuppressWarnings("unchecked")
  private <K, V> List<Map> createMapSummaryList(String itemName, Map<K,
      List<V>> map) {
    List<Map> summary = new ArrayList<Map>();
    for (Map.Entry<K, List<V>> entry : map.entrySet()) {
      Map item = new LinkedHashMap();
      item.put(itemName, entry.getKey());
      item.put("count", entry.getValue().size());
      summary.add(item);
    }
    return summary;
  }

  public static class ClientInfoImpl implements ClientInfo {
    private ClientInfo clientInfo;
    private int reservations;

    public ClientInfoImpl(ClientInfo clientInfo, int reservations) {
      this.clientInfo = clientInfo;
      this.reservations = reservations;
    }
    @Override
    public UUID getClientId() {
      return clientInfo.getClientId();
    }

    @Override
    public UUID getHandle() {
      return clientInfo.getHandle();
    }

    @Override
    public String getCallbackAddress() {
      return clientInfo.getCallbackAddress();
    }

    public int getReservations() {
      return reservations;
    }
  }

  private List<ClientInfoImpl> createClientInfoSummary() {
    Map<UUID, Integer> summary = new LinkedHashMap<UUID, Integer>();
    for (Map.Entry<UUID, List<PlacedReservation>> entry :
        clientReservationsMap.entrySet()) {
      summary.put(entry.getKey(), entry.getValue().size());
    }
    List<ClientInfoImpl> list = new ArrayList<ClientInfoImpl>(
        clientInfoMap.size());
    for (Map.Entry<UUID, ClientInfo> entry : clientInfoMap.entrySet()) {
      Integer count = summary.get(entry.getKey());
      count = (count != null) ? count : 0;
      list.add(new ClientInfoImpl(entry.getValue(), count));
    }
    return list;
  }

  @SuppressWarnings("unchecked")
  public void writeSummaryAsJson(Writer out)
      throws IOException, NotFoundException {
    lock.readLock().lock();
    try {
      Map summary = new LinkedHashMap();
      summary.put(VERSION_INFO_KEY, VERSION_INFO);
      summary.put(RESERVATIONS_COUNT_KEY, reservationsMap.size());
      summary.put(QUEUES_SUMMARY_KEY, createMapSummaryList("queue",
          queueReservationsMap));
      summary.put(CLIENTS_SUMMARY_KEY, createClientInfoSummary());
      summary.put(NODES_SUMMARY_KEY, createMapSummaryList("node",
          nodeResourcesMap));
      writeAsJson(SUMMARY_DATA, summary, out);
    } finally {
      lock.readLock().unlock();
    }
  }

  public void writeReservationAsJson(UUID reservationId, Writer out)
      throws IOException, NotFoundException {
    lock.readLock().lock();
    try {
      writeAsJson(RESERVATION_DATA, reservationsMap.get(reservationId), out);
    } finally {
      lock.readLock().unlock();
    }
  }

  @SuppressWarnings("unchecked")
  public void writeHandleReservationsAsJson(UUID handle, Writer out)
      throws IOException, NotFoundException {
    lock.readLock().lock();
    try {
      ClientInfo ci = clientInfoMap.get(handle);
      if (ci == null) {
        throw new NotFoundException();
      }
      List<PlacedReservation> prs = clientReservationsMap.get(handle);
      prs = (prs != null) ? prs : Collections.EMPTY_LIST;
      Map map = new LinkedHashMap();
      map.put("clientInfo", ci);
      map.put("reservations", prs);
      writeAsJson(HANDLE_DATA, map, out);
    } finally {
      lock.readLock().unlock();
    }
  }

  public void writeQueueReservationsAsJson(String queue, Writer out)
      throws IOException, NotFoundException {
    lock.readLock().lock();
    try {
      writeAsJson(QUEUE_DATA, queueReservationsMap.get(queue), out);
    } finally {
      lock.readLock().unlock();
    }
  }

  public void writeNodeResourcesAsJson(String node, Writer out)
      throws IOException, NotFoundException {
    lock.readLock().lock();
    try {
      writeAsJson(NODE_DATA, nodeResourcesMap.get(node), out);
    } finally {
      lock.readLock().unlock();
    }
  }

  public static final String ISO8601_UTC_MASK = "yyyy-MM-dd'T'HH:mm'Z'";
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  private static String formatDateTime(long epoc) {
    DateFormat dateFormat = new SimpleDateFormat(ISO8601_UTC_MASK);
    dateFormat.setTimeZone(UTC);
    Date date = new Date(epoc);
    return dateFormat.format(date);
  }

}
