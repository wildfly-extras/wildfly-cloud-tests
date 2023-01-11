/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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
package org.wildfly.test.cloud.elytron.oidc.client.autoreg;

import static io.dekorate.kubernetes.annotation.ImagePullPolicy.Always;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.HttpMethodConstraint;
import jakarta.servlet.annotation.ServletSecurity;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.ApplicationPath;

import io.dekorate.kubernetes.annotation.Env;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Port;
import io.dekorate.kubernetes.annotation.ServiceType;

/**
 * A simple secured HTTP servlet.
 *
 */
@KubernetesApplication(
        ports = @Port(nodePort = 30074, name = "http", containerPort = 8080),
        serviceType = ServiceType.NodePort,
        envVars = {
            @Env(name = "OIDC_PROVIDER_NAME", value = "keycloak"),
            @Env(name = "OIDC_USER_NAME", value = "demo"),
            @Env(name = "OIDC_USER_PASSWORD", value = "demo"),
            @Env(name = "OIDC_SECURE_DEPLOYMENT_SECRET", value = "mysecret"),
            @Env(name = "OIDC_PROVIDER_URL", value = "http://$CLUSTER_IP$:30075/realms/WildFly"),
            @Env(name = "OIDC_HOSTNAME_HTTP", value = "$CLUSTER_IP$:30074"),},
        imagePullPolicy = Always)
@ApplicationPath("")
@WebServlet("/secured")
@ServletSecurity(httpMethodConstraints = {
    @HttpMethodConstraint(value = "GET", rolesAllowed = {"Users"})})
public class SecuredServlet extends HttpServlet {

    /**
     * The String returned in the HTTP response body.
     */
    public static final String RESPONSE_BODY = "GOOD";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");
        final PrintWriter writer = resp.getWriter();
        writer.write(RESPONSE_BODY);
        writer.close();
    }

}
