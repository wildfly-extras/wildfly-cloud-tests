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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

import io.restassured.RestAssured;
import io.restassured.response.Response;

@WildFlyKubernetesIntegrationTest
public class WebClusteringTestCaseIT extends WildFlyCloudTestCase {

    private static final String APP_NAME = "web-clustering";

    @Test
    public void checkWebClustering() throws Exception {

        List<String> podNames = getHelper().kubectl().getPodNames("app.kubernetes.io/name=" + APP_NAME);
        Assertions.assertEquals(1, podNames.size(), "More than one pod found with expected label " + podNames);
        String firstPod = podNames.get(0);

        List<Map<String, String>> cookiesHolder = new ArrayList<>();
        Map<String, String> info = getHelper().doWithWebPortForward("", url -> {
            Response r = RestAssured.given().get(url);
            Assertions.assertEquals(200, r.getStatusCode());
            cookiesHolder.add(r.cookies());
            return r.as(Map.class);
        });

        // Scale to 2.
        getHelper().kubectl().scaleDeployment(APP_NAME, 2);
        // Wait for second pod to come up
        TimeUnit.SECONDS.sleep(10);
        podNames = getHelper().kubectl().getPodNames("app.kubernetes.io/name=" + APP_NAME);
        Assertions.assertEquals(2, podNames.size(), "Two pods should have been found " + podNames);

        // Wait for the first pod to fully sync by watching logs
        System.out.println("[TEST] Watch logs of first pod: " + firstPod);
        Process logStream = getHelper().kubectl().streamPodLog(firstPod);
        TimeUnit.SECONDS.sleep(20L);
        logStream.destroyForcibly();

        // Killing the pod we interacted with
        getHelper().kubectl().deletePod(firstPod);

        // Wait for the pod to be deleted
        TimeUnit.SECONDS.sleep(20L);

        Map<String, String> info2 = getHelper().doWithWebPortForward("", url -> {
            Response r = RestAssured.given().cookies(cookiesHolder.get(0)).get(url);
            Assertions.assertEquals(200, r.getStatusCode());
            return r.as(Map.class);
        });
        Assertions.assertEquals(info, info2);
    }

}
