/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.test.cloud.mpconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;

import io.dekorate.testing.annotation.Inject;
import io.dekorate.testing.annotation.KubernetesIntegrationTest;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@KubernetesIntegrationTest(readinessTimeout = 450000L)
public class EndpointTestCaseIT extends WildFlyCloudTestCase {

    private static final String CONTAINER_NAME = "wildfly-cloud-tests-core-env-vars-override-model";
    @Inject
    private KubernetesClient client;

    @Inject
    private KubernetesList list;

    @Test
    public void envVarOverridesManagementAttribute() throws Exception {
        String command = "/subsystem=logging/root-logger=ROOT:read-attribute(name=level)";
        ModelNode reply = getHelper().executeCLICommands(command);
        ModelNode result = getHelper().checkAndGetResult(reply);
        assertEquals("DEBUG", result.asString());
        httpClientCall();
    }

    @Test
    public void envVarsUsedAsExpressions() throws Exception {
        String addSystemProperty = "/system-property=test-property:add(value=\"\\${test-expression-from-property}\")";
        ModelNode result = getHelper().executeCLICommands(addSystemProperty);
        getHelper().checkAndGetResult(result);

        String resolveExpression = ":resolve-expression(expression=\"\\${test-property}\")";
        result = getHelper().executeCLICommands(resolveExpression);
        result = getHelper().checkAndGetResult(result);
        assertEquals("testing123", result.asString());
    }

    private void httpClientCall() throws Exception {
        getHelper().doWithWebPortForward("", (url) -> {
            Response r = RestAssured.get(url);
            assertEquals("{\"result\":\"OK\"}", r.getBody().asString());
            return null;
        });
    }
}
