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
package org.wildfly.cloud.tests.framework.app;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("")
public class FrameworkTestEndpoint {

    @Inject
    @ConfigProperty(name = "propertyA", defaultValue = "N/A")
    private String propertyA;

    @Inject
    @ConfigProperty(name = "propertyB", defaultValue = "N/A")
    private String propertyB;

    @Inject
    @ConfigProperty(name = "propertyC", defaultValue = "N/A")
    private String propertyC;

    @Inject
    @ConfigProperty(name = "propertyD", defaultValue = "N/A")
    private String propertyD;

    @GET
    @Produces(APPLICATION_JSON)
    public FrameworkTestValues getConfiguration() {
        FrameworkTestValues values =
                new FrameworkTestValues(
                        propertyA,
                        propertyB,
                        propertyC,
                        propertyD
                );
        
        return values;
    }
}