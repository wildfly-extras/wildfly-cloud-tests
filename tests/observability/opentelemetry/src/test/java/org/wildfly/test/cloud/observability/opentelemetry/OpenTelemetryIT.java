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

import io.restassured.RestAssured;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

@WildFlyKubernetesIntegrationTest
public class OpenTelemetryIT extends WildFlyCloudTestCase {

    @Test
    public void smokeTest() throws Exception {
        getHelper().doWithWebPortForward("", url ->
                RestAssured.given()
                        .get(url)
                        .then()
                        .assertThat()
                        .statusCode(200));

        String podName = getHelper().kubectl().getPodNames("app.kubernetes.io/name=opentelemetrycollector").get(0);
        boolean found = false;
        int count = 0;
        while (count < 10) {
            String podLogs = getHelper().getPodLog(podName);
            found = podLogs.contains(OpenTelemetryEndpoint.TEST_SPAN) &&
                    podLogs.contains(OpenTelemetryEndpoint.TEST_EVENT);

            if (!found) {
                Thread.sleep(2000);
                count++;
            } else {
                break;
            }
        }

        Assertions.assertTrue(found, "Expected log entries were not found.");
    }
}
