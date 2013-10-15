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
package com.cloudera.llama.util;


public class UUID {
  private java.util.UUID uuid;

  public UUID(java.util.UUID uuid) {
    this.uuid = uuid;
  }

  public UUID(long mostSigBits, long leastSigBits) {
    this(new java.util.UUID(mostSigBits, leastSigBits));
  }

  public static UUID randomUUID() {
    return new UUID(java.util.UUID.randomUUID());
  }

  public long getLeastSignificantBits() {
    return uuid.getLeastSignificantBits();
  }

  public long getMostSignificantBits() {
    return uuid.getMostSignificantBits();
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof UUID) && uuid.equals(((UUID) obj).uuid);
  }

  public static UUID fromString(String name) {
    String[] components = new String[5];
    components[0] = "0x" + name.substring(0, 8);
    components[1] = "0x" + name.substring(8, 8 + 4);
    components[2] = "0x" + name.substring(12, 12 + 4);
    components[3] = "0x" + name.substring(16, 16 + 4);
    components[4] = "0x" + name.substring(20, 20 + 12);

    long mostSigBits = Long.decode(components[0]);
    mostSigBits <<= 16;
    mostSigBits |= Long.decode(components[1]);
    mostSigBits <<= 16;
    mostSigBits |= Long.decode(components[2]);

    long leastSigBits = Long.decode(components[3]);
    leastSigBits <<= 48;
    leastSigBits |= Long.decode(components[4]);

    return new UUID(mostSigBits, leastSigBits);
  }


  @Override
  public String toString() {
    long mostSigBits = uuid.getMostSignificantBits();
    long leastSigBits = uuid.getLeastSignificantBits();
    return (digits(mostSigBits >> 32, 8) +
        digits(mostSigBits >> 16, 4) +
        digits(mostSigBits, 4) +
        digits(leastSigBits >> 48, 4) +
        digits(leastSigBits, 12));
  }

  private static String digits(long val, int digits) {
    long hi = 1L << (digits * 4);
    return Long.toHexString(hi | (val & (hi - 1))).substring(1);
  }
}
