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

package org.wildfly.test.cloud.microprofile.reactive.messaging.strimzi;

import io.dekorate.testing.WithKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.wildfly.test.cloud.common.ConfigPlaceholderReplacement;
import org.wildfly.test.cloud.common.ConfigPlaceholderReplacer;
import org.wildfly.test.cloud.common.KubernetesResource;
import org.wildfly.test.cloud.common.Resource;
import org.wildfly.test.cloud.common.ResourceType;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

import java.util.ArrayList;
import java.util.List;

import static org.wildfly.test.cloud.common.WildflyTags.KUBERNETES;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@Tag(KUBERNETES)
@WildFlyKubernetesIntegrationTest(
        namespace = "kafka",
        kubernetesResources = {
                //@KubernetesResource(definitionLocation = "https://strimzi.io/install/latest?namespace=kafka"),
                // Local copy of the above file with a '$NAMESPACE$' placeholder
                @KubernetesResource(definitionLocation = "src/test/container/strimzi.yml"),
                @KubernetesResource(
                        definitionLocation = "src/test/container/strimzi-cluster.yml",
                        additionalResourcesCreated = {
                                @Resource(type = ResourceType.DEPLOYMENT, name = "my-cluster-entity-operator")
                        }),
                @KubernetesResource(definitionLocation = "src/test/container/strimzi-topic.yml")
        },
        placeholderReplacements = {
            @ConfigPlaceholderReplacement(
                    placeholder = "$NAMESPACE$",
                    replacer = ReactiveMessagingWithStrimziIT.NamespaceReplacer.class)}
        )
public class ReactiveMessagingWithStrimziIT  extends WildFlyCloudTestCase {

    @Test
    public void test() throws Exception {
        postMessage("one");

        List<String> list = getReceived();
        if (list.size() == 0) {
            // Occasionally we might start sending messages before the subscriber is connected property
            // (the connection happens async as part of the application start) so retry until we get this first message
            Thread.sleep(1000);
            long end = System.currentTimeMillis() + 20000;
            while (true) {
                list = getReceived();
                if (getReceived().size() != 0) {
                    break;
                }

                if (System.currentTimeMillis() > end) {
                    break;
                }
                postMessage("one");
                Thread.sleep(1000);
            }
        }


        postMessage("two");

        long end = System.currentTimeMillis() + 20000;
        while (list.size() != 2 && System.currentTimeMillis() < end) {
            list = getReceived();
            Thread.sleep(1000);
        }
        waitUntilListPopulated(20000, "one", "two");

    }

    private void waitUntilListPopulated(long timoutMs, String... expected) throws Exception {
        List<String> list = new ArrayList<>();
        long end = System.currentTimeMillis() + timoutMs;
        while (list.size() < expected.length && System.currentTimeMillis() < end) {
            list = getReceived();
            Thread.sleep(1000);
        }
        Assertions.assertArrayEquals(expected, list.toArray(new String[list.size()]));
    }

    private List<String> getReceived() throws Exception {
        return getHelper().doWithWebPortForward("", url -> {
            Response r = RestAssured.get(url);
            Assertions.assertEquals(200, r.getStatusCode());
            return r.as(List.class);
        });
    }

    private void postMessage(String s) throws Exception {
        int status = getHelper().doWithWebPortForward(s,
                url -> RestAssured.given().header("Content-Type", MediaType.TEXT_PLAIN).post(url).getStatusCode());
        Assertions.assertEquals(200, status);

    }

    public static class NamespaceReplacer implements ConfigPlaceholderReplacer, WithKubernetesClient {
        @Override
        public String replace(ExtensionContext context, String placeholder, String line) {
            if (line.contains("$NAMESPACE$")) {
                KubernetesClient client = getKubernetesClient(context);
                String namespace = client.getNamespace();
                System.out.println("Replacing $NAMESPACE$ with Cluster IP address " + namespace);
                line = line.replace("$NAMESPACE$", namespace);
            }
            return line;
        }
    }

}
