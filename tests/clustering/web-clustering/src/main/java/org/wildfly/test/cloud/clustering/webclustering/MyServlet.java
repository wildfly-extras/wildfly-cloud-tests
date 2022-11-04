/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.test.cloud.clustering.webclustering;

import static io.dekorate.kubernetes.annotation.ImagePullPolicy.Always;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.dekorate.kubernetes.annotation.Env;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Port;
import io.dekorate.kubernetes.annotation.ServiceType;
import io.dekorate.openshift.annotation.OpenshiftApplication;
/**
 *
 * @author jdenise
 */
@KubernetesApplication(
        envVars = {
            @Env(name = "JGROUPS_PING_PROTOCOL", value = "dns.DNS_PING"),
            @Env(name = "OPENSHIFT_DNS_PING_SERVICE_PORT", value = "8888"),
            @Env(name = "OPENSHIFT_DNS_PING_SERVICE_NAME", value = "wildfly-cloud-tests-web-clustering-ping")},
        imagePullPolicy = Always,
        replicas = 1)
@WebServlet(urlPatterns = {"/"})
public class MyServlet extends HttpServlet {

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        long t = 0;
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) {
            t = System.currentTimeMillis();
            user = new User(t);
            request.getSession().setAttribute("user", user);
        }
        try ( PrintWriter out = response.getWriter()) {
            out.println("{\n");
            out.println("\"session\": \"" + request.getSession().getId() + "\",\n");
            out.println("\"user\": \"" + user.getCreated() + "\"\n");
            out.println("}\n");
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
