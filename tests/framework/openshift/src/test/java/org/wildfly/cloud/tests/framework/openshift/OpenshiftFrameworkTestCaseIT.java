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
package org.wildfly.cloud.tests.framework.openshift;

import io.dekorate.testing.annotation.Inject;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.wildfly.cloud.tests.framework.app.FrameworkTestValues;
import org.wildfly.test.cloud.common.ConfigPlaceholderReplacement;
import org.wildfly.test.cloud.common.KubernetesResource;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyOpenshiftIntegrationTest;

import static org.wildfly.test.cloud.common.WildflyTags.OPENSHIFT;

@Tag(OPENSHIFT)
@WildFlyOpenshiftIntegrationTest(
        pushEnabled = true,
        kubernetesResources = {
                @KubernetesResource(definitionLocation = "src/test/container/resource-c.yml"),
                @KubernetesResource(definitionLocation = "src/test/container/resource-d.yml"),
        },
        extraTestSetup = OpenshiftExtraTestSetup.class,
        placeholderReplacements = {
                @ConfigPlaceholderReplacement(placeholder = "$NAME-B$", replacer = Replacers.NameReplacer.class),
                @ConfigPlaceholderReplacement(placeholder = "$VALUE-B$", replacer = Replacers.ValueReplacer.class),
                @ConfigPlaceholderReplacement(placeholder = "$NAME-D$", replacer = Replacers.NameReplacer.class),
                @ConfigPlaceholderReplacement(placeholder = "$VALUE-D$", replacer = Replacers.ValueReplacer.class),
        }
)
public class OpenshiftFrameworkTestCaseIT extends WildFlyCloudTestCase {

    @Inject
    private KubernetesClient client;

    @Inject
    private KubernetesList list;

    @Test
    public void checkMPConfig() throws Exception {
        Assertions.assertTrue(OpenshiftExtraTestSetup.called);
        getHelper().doWithWebPortForward("", (url) -> {
            Response r = RestAssured.get(url);
            FrameworkTestValues values = r.getBody().as(FrameworkTestValues.class);
            Assertions.assertEquals("A", values.getPropertyA());
            Assertions.assertEquals("B", values.getPropertyB());
            Assertions.assertEquals("C", values.getPropertyC());
            Assertions.assertEquals("D", values.getPropertyD());
            return null;
        });
    }

}
