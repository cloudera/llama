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
package com.cloudera.llama.am.cache;

import com.cloudera.llama.am.spi.RMResource;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.util.ParamChecker;
import com.cloudera.llama.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ResourceStore {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceStore.class);

  private static int VCORE_LIST = 0;
  private static int MEM_LIST = 1;

  private final Map<String, List<Entry>[]> nodeToEntries;
  private final Map<UUID, Entry> idToEntryMap;

  public ResourceStore() {
    nodeToEntries = new TreeMap<String, List<Entry>[]>();
    idToEntryMap = new HashMap<UUID, Entry>();
  }

  public synchronized void add(Entry entry) {
    entry.setValid(true);
    idToEntryMap.put(entry.getResourceId(), entry);

    String node = entry.getLocation();
    List<Entry>[] entries = nodeToEntries.get(node);
    if (entries == null) {
      entries = new List[] {
        new LinkedList<Entry>(),
        new LinkedList<Entry>()
      };
      nodeToEntries.put(node, entries);
    }
    addEntry(entries, entry);
  }

  private enum Mode {SAME_REF, STRICT_LOCATION, ANY_LOCATION}

  private Entry findAndRemove(Entry entry, Mode mode) {
    ParamChecker.notNull(entry, "entry");
    ParamChecker.notNull(mode, "mode");
    Entry found = null;

    switch (mode) {
    case STRICT_LOCATION:
      List<Entry>[] entries = nodeToEntries.get(entry.getLocation());
      found = removeEntry(entries, entry);
      break;
    case SAME_REF:
      entries = nodeToEntries.get(entry.getLocation());
      if (entries != null) {
        List<Entry> list =
            (entry.getCpuVCores() > 0) ? entries[VCORE_LIST] : entries[MEM_LIST];

        for (int i = 0; i < list.size(); i++) {
          Entry e = list.get(i);
          if (e == entry) {
            list.remove(i);
            found = e;
            break;
          }
        }
      }
      break;
    case ANY_LOCATION:
      // Lets try to give in the location that asked for or else
      // return from the first one..
      if (entry.getLocation() != null) {
        entries = nodeToEntries.get(entry.getLocation());
        found = removeEntry(entries, entry);
      }
      if (found == null && nodeToEntries.size() > 0) {
        Set<String> key = nodeToEntries.keySet();
        found = removeEntry(nodeToEntries.get(key.iterator().next()), entry);
      }
      break;
    }

    if (found != null) {
      idToEntryMap.remove(found.getResourceId());
      found.setValid(false);

      if (found.getCpuVCores() != entry.getCpuVCores() &&
          found.getMemoryMbs() != entry.getMemoryMbs()) {
        LOG.error("Cache store inconsistent state: Ask cpu: {}, mem: {}. " +
            "Give cpu: {}, mem {}",
            entry.getCpuVCores(),
            entry.getMemoryMbs(),
            found.getCpuVCores(), found.getMemoryMbs());
      }
    }
    return found;
  }

  private void addEntry(List<Entry>[] entries, Entry entry) {
    if (entry.getCpuVCores() > 0 && entry.getMemoryMbs() > 0) {
      throw new IllegalArgumentException("Cannot add entries which have both cpu and memory");
    }
    if (entry.getCpuVCores() > 0) {
      entries[VCORE_LIST].add(entry);
    }
    if (entry.getMemoryMbs() > 0) {
      entries[MEM_LIST].add(entry);
    }
  }

  private Entry removeEntry(List<Entry>[] entries, Entry entry) {
    if (entries != null) {
      if (entry.getCpuVCores() > 0 && entries[0].size() > 0) {
        return entries[VCORE_LIST].remove(0);
      } else if (entry.getMemoryMbs() > 0 && entries[1].size() > 0) {
        return entries[MEM_LIST].remove(0);

      }
    }
    return null;
  }

  public synchronized Entry findAndRemove(RMResource resource) {
    Mode mode = (resource.getLocalityAsk() == Resource.Locality.MUST)
                ? Mode.STRICT_LOCATION : Mode.ANY_LOCATION;
    return findAndRemove(new Entry(resource.getLocationAsk(),
        resource.getCpuVCoresAsk(), resource.getMemoryMbsAsk()), mode);
  }

  public synchronized Entry findAndRemove(UUID storeId) {
    Entry found = idToEntryMap.remove(storeId);
    if (found != null && findAndRemove(found, Mode.SAME_REF) == null) {
      LOG.error("Inconsistency in for storeId '{}' rmResourceId '{}'",
          storeId, found.getRmResourceId());
    }
    return found;
  }

  protected synchronized List<Entry> getEntries() {
    return new ArrayList<Entry>(idToEntryMap.values());
  }

  public synchronized List<RMResource> emptyStore() {
    List<RMResource> list = new ArrayList<RMResource>(idToEntryMap.values());
    idToEntryMap.clear();
    nodeToEntries.clear();
    return list;
  }

  public synchronized int getSize() {
    return idToEntryMap.size();
  }

}
