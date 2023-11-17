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

import static org.wildfly.test.cloud.common.WildflyTags.KUBERNETES;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import jakarta.ws.rs.core.MediaType;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.KubernetesResource;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

/**
 * @author <a href="mailto:jasondlee@redhat.com">Jason Lee</a>
 */
@Tag(KUBERNETES)
@WildFlyKubernetesIntegrationTest(
        namespace = "micrometer",
        kubernetesResources = {
                @KubernetesResource(definitionLocation = "src/test/container/collector-config.yml"),
                @KubernetesResource(definitionLocation = "src/test/container/opentelemetry-collector.yml"),
                @KubernetesResource(definitionLocation = "src/test/container/service.yml")
        })
public class MicrometerIT extends WildFlyCloudTestCase {

    @Test
    public void test() throws Exception {
        int requestCount = (int) ((Math.random() * 10) + 10);
        getHelper().doWithWebPortForward("", url -> makeRestRequests(url, requestCount));


        try (LocalPortForward p = getHelper().getK8sClient().services().withName("opentelemetrycollector").portForward(1234)) {
            URI uri = new URI("http://localhost:" + p.getLocalPort() + "/metrics");

            final HttpClient client = HttpClient.newBuilder().build();
            final HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();

            boolean found = false;
            int count = 0;
            while (count < 10) {
                final String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
                found = response.contains("hello_total{job=\"wildfly\"} " + requestCount);
                if (!found) {
                    Thread.sleep(1000);
                    count++;
                } else {
                    break;
                }
            }
            Assertions.assertTrue(found, "The test metric 'hello' was not found in the publish metrics.");
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
