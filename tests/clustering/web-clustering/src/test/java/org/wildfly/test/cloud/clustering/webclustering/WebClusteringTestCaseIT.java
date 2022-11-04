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
package org.wildfly.test.cloud.clustering.webclustering;

import static org.wildfly.test.cloud.common.WildflyTags.KUBERNETES;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.KubernetesResource;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

import io.dekorate.testing.annotation.Inject;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
@Tag(KUBERNETES)
@WildFlyKubernetesIntegrationTest(
        kubernetesResources = {
            @KubernetesResource(
                    definitionLocation = "src/test/container/ping-service.yml"
            ),})
public class WebClusteringTestCaseIT extends WildFlyCloudTestCase {

    @Inject
    private KubernetesClient client;

    @Test
    public void checkWebClustering() throws Exception {
                
        List<Pod> lst = client.pods().withLabel("app.kubernetes.io/name", "wildfly-cloud-tests-web-clustering").list().getItems();
        Assertions.assertEquals(1, lst.size(), "More than one pod found with expected label " + lst);
        Pod first = lst.get(0);
        // Get the session content from the first pod.
        List<Map<String, String>> cookiesHolder = new ArrayList<>();
        Map<String, String> info = getHelper().doWithWebPortForward("", url -> {
            Response r = RestAssured.given().get(url);
            Assertions.assertEquals(200, r.getStatusCode());
            cookiesHolder.add(r.cookies());
            return r.as(Map.class);
        });
        
        // Scale to 2.
        client.apps().deployments().withName("wildfly-cloud-tests-web-clustering").scale(2, true);
        lst = client.pods().withLabel("app.kubernetes.io/name", "wildfly-cloud-tests-web-clustering").list().getItems();
        Assertions.assertEquals(2, lst.size(), "Two pods should have been found " + lst);
        
        // Wait for the first pod to fully sync by watching logs for 20 seconds
        System.out.println("[TEST] Watch logs of first pod: " + first.getMetadata().getName());
        client.pods().withName(first.getMetadata().getName()).watchLog(System.out);
        TimeUnit.SECONDS.sleep(20L);

        // Killing the pod we interacted with. It means that session content will be retrieved from replicated content in second pod.
        client.pods().delete(first);
        
         // Wait for the first pod to be deleted, safer when we kill a pod
        System.out.println("[TEST] Watch logs of first pod: " + first.getMetadata().getName());
        client.pods().withName(first.getMetadata().getName()).watchLog(System.out);
        TimeUnit.SECONDS.sleep(20L);

        Map<String, String> info2 = getHelper().doWithWebPortForward("", url -> {
            Response r = RestAssured.given().cookies(cookiesHolder.get(0)).get(url);
            Assertions.assertEquals(200, r.getStatusCode());
            return r.as(Map.class);
        });
        Assertions.assertEquals(info, info2);
    }

}
