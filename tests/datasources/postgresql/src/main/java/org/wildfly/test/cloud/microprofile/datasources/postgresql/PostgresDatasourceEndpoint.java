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

package org.wildfly.test.cloud.microprofile.datasources.postgresql;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@Path("")
public class PostgresDatasourceEndpoint {
    @Inject
    PostgresTransactionalBean bean;

    @POST
    @Path("gp/{value}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response storeInGallonPackDs(@PathParam("value") String value) {
        bean.storeValueInGalleonPackDs(value);
        return Response.ok().build();
    }

    @GET
    @Path("gp")
    @Produces(APPLICATION_JSON)
    public List<String> getAllGalleonPackDsValues() {
        return bean.getAllGalleonPackDsValues();
    }

    @POST
    @Path("ls/{value}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response storeInLaunchScriptDs(@PathParam("value") String value) {
        bean.storeValueInLaunchScriptDs(value);
        return Response.ok().build();
    }

    @GET
    @Path("ls")
    @Produces(APPLICATION_JSON)
    public List<String> getAllLaunchScriptDsValues() {
        return bean.getAllLaunchScriptDsValues();
    }

}
