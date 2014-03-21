package com.cloudera.llama.am.api;

import com.cloudera.llama.util.FastFormat;

public class NodeInfo {
  final private String location;
  private int cpus;
  private long memoryMB;

  public NodeInfo(String location, int cpus, long memoryMB) {
    this.location = location;
    this.cpus = cpus;
    this.memoryMB = memoryMB;
  }

  public NodeInfo(String location) {
    this(location, 0, 0);
  }

  public String getLocation() {
    return location;
  }

  public int getCpusVCores() {
    return cpus;
  }

  public long getMemoryMB() {
    return memoryMB;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NodeInfo nodeInfo = (NodeInfo) o;
    if (!location.equals(nodeInfo.location)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return location.hashCode();
  }

  private static final String TO_STRING =
      "NodeInfo [location: {}, cpus: {}, memoryMB: {}]";

  @Override
  public String toString() {
    return FastFormat.format(TO_STRING, location, cpus, memoryMB);
  }

  public void setCpus(int cpusAsk) {
    this.cpus = cpusAsk;
  }

  public void setMemoryMB(int memoryMbsAsk) {
    this.memoryMB = memoryMbsAsk;
  }
}
