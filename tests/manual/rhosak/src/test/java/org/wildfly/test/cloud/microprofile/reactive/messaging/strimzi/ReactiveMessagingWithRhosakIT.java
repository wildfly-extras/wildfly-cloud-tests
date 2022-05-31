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

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@WildFlyKubernetesIntegrationTest(
        extraTestSetup = RhosakCliAdditionalTestSetup.class
)
public class ReactiveMessagingWithRhosakIT extends WildFlyCloudTestCase {

    @Test
    public void test() throws Exception {
        int status = getHelper().doWithWebPortForward("one",
                url -> RestAssured.given().header("Content-Type", MediaType.TEXT_PLAIN).post(url).getStatusCode());
        Assertions.assertEquals(200, status);
        status = getHelper().doWithWebPortForward("two",
                url -> RestAssured.given().header("Content-Type", MediaType.TEXT_PLAIN).post(url).getStatusCode());
        Assertions.assertEquals(200, status);

        List<String> list = new ArrayList<>();
        long end = System.currentTimeMillis() + 20000;
        while (list.size() != 2 && System.currentTimeMillis() < end) {
            list = getHelper().doWithWebPortForward("", url -> {
                Response r = RestAssured.get(url);
                Assertions.assertEquals(200, r.getStatusCode());
                return r.as(List.class);
            });
            Thread.sleep(1000);
        }

        Assertions.assertArrayEquals(new String[]{"one", "two"}, list.toArray(new String[list.size()]));
    }
}
