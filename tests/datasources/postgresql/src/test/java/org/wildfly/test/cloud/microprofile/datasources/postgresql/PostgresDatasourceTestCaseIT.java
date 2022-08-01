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

import static org.wildfly.test.cloud.common.WildflyTags.KUBERNETES;

import java.util.List;

import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

import io.dekorate.testing.annotation.Inject;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@Tag(KUBERNETES)
@WildFlyKubernetesIntegrationTest
@Disabled("Disabled because the currently released datasource " +
        "feature pack depends on an older javax. version of WildFly. " +
        "When provisioning it pulls in an old version of WildFly which " +
        "does not recognise the jakarta. annotations in the endpoint. ")
public class PostgresDatasourceTestCaseIT extends WildFlyCloudTestCase {

    @Inject
    private KubernetesClient client;

    @Inject
    private KubernetesList list;

    @Test
    public void checkMPConfig() throws Exception {
        int status = getHelper().doWithWebPortForward("gp/first",
                url -> RestAssured.given().header("Content-Type", MediaType.TEXT_PLAIN).post(url).getStatusCode());
        Assertions.assertEquals(200, status);
        status = getHelper().doWithWebPortForward("gp/second",
                url -> RestAssured.given().header("Content-Type", MediaType.TEXT_PLAIN).post(url).getStatusCode());
        Assertions.assertEquals(200, status);

        status = getHelper().doWithWebPortForward("ls/third",
                url -> RestAssured.given().header("Content-Type", MediaType.TEXT_PLAIN).post(url).getStatusCode());
        Assertions.assertEquals(200, status);
        status = getHelper().doWithWebPortForward("ls/fourth",
                url -> RestAssured.given().header("Content-Type", MediaType.TEXT_PLAIN).post(url).getStatusCode());
        Assertions.assertEquals(200, status);

        List<String> gpDsEntries = getHelper().doWithWebPortForward("gp", url -> {
            Response r = RestAssured.get(url);
            Assertions.assertEquals(200, r.getStatusCode());
            return r.as(List.class);
        });

        List<String> lsDsEntries = getHelper().doWithWebPortForward("ls", url -> {
            Response r = RestAssured.get(url);
            Assertions.assertEquals(200, r.getStatusCode());
            return r.as(List.class);
        });

        Assertions.assertArrayEquals(new String[]{"first", "second"}, gpDsEntries.toArray(new String[gpDsEntries.size()]));
        Assertions.assertArrayEquals(new String[]{"third", "fourth"}, lsDsEntries.toArray(new String[gpDsEntries.size()]));
    }

}
