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

import io.dekorate.testing.annotation.Inject;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@WildFlyKubernetesIntegrationTest
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
