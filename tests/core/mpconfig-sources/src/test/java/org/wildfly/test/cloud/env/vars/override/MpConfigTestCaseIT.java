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
package org.wildfly.test.cloud.env.vars.override;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

import io.dekorate.testing.annotation.Inject;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import static org.wildfly.test.cloud.common.WildflyTags.KUBERNETES;

@Tag(KUBERNETES)
@WildFlyKubernetesIntegrationTest
public class MpConfigTestCaseIT extends WildFlyCloudTestCase {

    @Inject
    private KubernetesClient client;

    @Inject
    private KubernetesList list;

    @Test
    public void checkMPConfig() throws Exception {
        getHelper().doWithWebPortForward("", (url) -> {
            Response r = RestAssured.get(url);
            MpConfigValues values = r.getBody().as(MpConfigValues.class);
            Assertions.assertEquals("From env var", values.getConfigEnvVar());
            Assertions.assertEquals("From deployment", values.getDeploymentProperty());
            Assertions.assertEquals("From config map", values.getConfigMapProperty());
            Assertions.assertEquals("From secret", values.getSecretProperty());
            return null;
        });
    }

}
