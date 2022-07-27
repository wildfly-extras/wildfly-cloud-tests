/*
 * JBoss, Home of Professional Open Source.
 *  Copyright 2022 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.wildfly.test.cloud.env.vars.override;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("")
public class MpConfigEndpoint {

    @Inject
    @ConfigProperty(name = "config.env.var", defaultValue = "N/A")
    private String configEnvVar;

    @Inject
    @ConfigProperty(name = "config-property-from-deployment", defaultValue = "N/A")
    private String deploymentProperty;

    @Inject
    @ConfigProperty(name = "config.map.property", defaultValue = "N/A")
    private String configMapProperty;

    @Inject
    @ConfigProperty(name = "secret.property", defaultValue = "N/A")
    private String secretProperty;

    @GET
    @Produces(APPLICATION_JSON)
    public MpConfigValues getConfiguration() {
        MpConfigValues values = new MpConfigValues();
        values.setConfigEnvVar(configEnvVar);
        values.setDeploymentProperty(deploymentProperty);
        values.setConfigMapProperty(configMapProperty);
        values.setSecretProperty(secretProperty);
        return values;
    }
}