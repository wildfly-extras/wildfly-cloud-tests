/*
 * JBoss, Home of Professional Open Source.
 *  Copyright 2023 Red Hat, Inc., and individual contributors
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
package org.wildfly.test.cloud.observability.opentelemetry;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

@Path("")
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.MEDIA_TYPE_WILDCARD)
public class OpenTelemetryEndpoint {
    public static final String TEST_SPAN = "Test span";
    public static final String TEST_EVENT = "Test event";
    @Inject
    private Tracer tracer;

    @GET
    public String hello() {
        final Span span = tracer.spanBuilder(TEST_SPAN).startSpan();

        span.makeCurrent();
        span.addEvent(TEST_EVENT);
        span.end();

        return "hello";
    }
}
