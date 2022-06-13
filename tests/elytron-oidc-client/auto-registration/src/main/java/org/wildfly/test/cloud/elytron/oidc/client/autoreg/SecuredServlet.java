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

import io.dekorate.kubernetes.annotation.Env;
import static io.dekorate.kubernetes.annotation.ImagePullPolicy.Always;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Port;
import io.dekorate.kubernetes.annotation.ServiceType;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpMethodConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ApplicationPath;

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
            @Env(name = "OIDC_PROVIDER_URL", value = "http://192.168.49.2:30075/auth/realms/WildFly"),
            @Env(name = "OIDC_HOSTNAME_HTTP", value = "192.168.49.2:30074"),},
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
