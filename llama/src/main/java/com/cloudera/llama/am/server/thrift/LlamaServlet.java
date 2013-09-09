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

import org.apache.hadoop.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class LlamaServlet extends HttpServlet {

  //TODO: FIX THIS, this is a nasty hack so the page works when proxy-fied by
  // Yarn
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (req.getPathInfo() != null){
      if (req.getPathInfo().equals("/llama")) {
        resp.sendRedirect("/llama/index.html");
      } else if (req.getPathInfo().equals("/llama/")) {
        resp.sendRedirect("/llama/index.html");
      } else if (req.getPathInfo().equals("/llama/llama")) {
        resp.sendRedirect("/llama/index.html");
      } else if (req.getPathInfo().equals("/llama/index.html")) {
        resp.setContentType("text/html");
        resp.setStatus(HttpServletResponse.SC_OK);
        IOUtils.copyBytes(cl.getResourceAsStream("llama.html"),
            resp.getOutputStream(), 1024);
      } else if (req.getPathInfo().equals("/llama/llama.png")) {
        resp.setContentType("image/png");
        resp.setStatus(HttpServletResponse.SC_OK);
        IOUtils.copyBytes(cl.getResourceAsStream("llama.png"),
            resp.getOutputStream(), 1024);
      } else if (req.getPathInfo().equals("/llama/monkey.gif")) {
        resp.setContentType("image/gif");
        resp.setStatus(HttpServletResponse.SC_OK);
        IOUtils.copyBytes(cl.getResourceAsStream("monkey.gif"),
            resp.getOutputStream(), 1024);
      } else {
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      }
    } else {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

}
