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

package org.wildfly.test.cloud.observability.micrometer;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import io.restassured.RestAssured;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

@WildFlyKubernetesIntegrationTest
public class MicrometerIT extends WildFlyCloudTestCase {

    @Test
    public void test() throws Exception {
        int requestCount = (int) ((Math.random() * 10) + 10);
        getHelper().doWithWebPortForward("", url -> makeRestRequests(url, requestCount));

        Process collectorPortForward = getHelper().kubectl().portForward("svc/opentelemetrycollector", 1234, 1234);
        try {
            Thread.sleep(1000);
            URI uri = new URI("http://localhost:1234/metrics");

            final HttpClient client = HttpClient.newBuilder().build();
            final HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();

            boolean found = false;
            int count = 0;
            while (count < 10) {
                String[] lines = client.send(request, HttpResponse.BodyHandlers.ofString()).body().split("\n");
                found = Arrays.stream(lines).anyMatch(line ->
                        line.startsWith("hello_total") && line.endsWith("" + requestCount)
                );
                if (!found) {
                    Thread.sleep(1000);
                    count++;
                } else {
                    break;
                }
            }

            Assertions.assertTrue(found, "The test metric 'hello' was not found in the published metrics.");
        } finally {
            collectorPortForward.destroyForcibly();
        }
    }

    private static String makeRestRequests(URL url, int requestCount) {
        String result = "";

        for (int i = 0; i < requestCount; i++) {
            result = RestAssured.given()
                    .header("Content-Type", MediaType.TEXT_PLAIN)
                    .header("Accept", MediaType.TEXT_PLAIN)
                    .get(url)
                    .thenReturn()
                    .asString();
        }

        return result;
    }
}
