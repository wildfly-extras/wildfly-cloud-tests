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
package org.wildfly.cloud.test.openshift.poc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyOpenshiftIntegrationTest;
import org.wildfly.test.cloud.common.WildflyTags;

import io.restassured.RestAssured;
import io.restassured.response.Response;

@Tag(WildflyTags.OPENSHIFT)
@WildFlyOpenshiftIntegrationTest(pushEnabled = true)
public class EnvVarsOverrideOpenShiftTestCaseIT extends WildFlyCloudTestCase {
    @Test
    public void envVarOverridesManagementAttribute() throws Exception {
        String command = "/subsystem=logging/root-logger=ROOT:read-attribute(name=level)";
        ModelNode reply = getHelper().executeCLICommands(command);
        ModelNode result = getHelper().checkAndGetResult(reply);
        assertEquals("DEBUG", result.asString());
        httpClientCall();
    }

//    @Test
//    public void envVarsUsedAsExpressions() throws Exception {
//        String addSystemProperty = "/system-property=test-property:add(value=\"${test-expression-from-property}\")";
//        ModelNode result = getHelper().executeCLICommands(addSystemProperty);
//        getHelper().checkAndGetResult(result);
//
//        String resolveExpression = ":resolve-expression(expression=\"${test-property}\")";
//        result = getHelper().executeCLICommands(resolveExpression);
//        result = getHelper().checkAndGetResult(result);
//        assertEquals("testing123", result.asString());
//    }

    private void httpClientCall() throws Exception {
        getHelper().doWithWebPortForward("", (url) -> {
            Response r = RestAssured.get(url);
            assertEquals("{\"result\":\"OK\"}", r.getBody().asString());
            return null;
        });
    }
}
