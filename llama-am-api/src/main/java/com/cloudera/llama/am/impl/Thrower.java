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
package com.cloudera.llama.am.impl;

/**
 * Exception Utility class.
 */
public class Thrower {

  /**
   * Throws a checked exception without having to declare it.
   * <p/>
   * Idea from:
   * http://www.eishay.com/2011/11/throw-undeclared-checked-exception-in.html
   * (Apache License)
   *
   * @param ex exception to throw.
   * @throws throws the given exception.
   */
  public static void throwEx(Throwable ex) {
    Thrower.<RuntimeException>throwException(ex);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwException(Throwable ex)
      throws E {
    throw (E) ex;
  }

}
