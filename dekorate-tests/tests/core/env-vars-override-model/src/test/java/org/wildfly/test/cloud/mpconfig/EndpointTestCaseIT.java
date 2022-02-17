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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.test.cloud.common.TestHelper.waitUntilWildFlyIsReady;

import java.io.IOException;
import java.net.URL;

import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.TestHelper;

import io.dekorate.testing.annotation.Inject;
import io.dekorate.testing.annotation.KubernetesIntegrationTest;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@KubernetesIntegrationTest(readinessTimeout = 450000L)
public class EndpointTestCaseIT {

    private static final String CONTAINER_NAME = "wildfly-cloud-dekorate-tests-core-env-vars-override-model";
    @Inject
    private KubernetesClient client;

    @Inject
    private KubernetesList list;

    @BeforeEach
    public void waitForWildFlyReadiness() {
        Pod pod = client.pods().list().getItems().get(0);
        String podName = pod.getMetadata().getName();
        waitUntilWildFlyIsReady(client, podName, CONTAINER_NAME, 30000);
    }

    @Test
    public void envVarOverridesManagementAttribute() throws IOException {
        Pod pod = client.pods().list().getItems().get(0);
        String podName = pod.getMetadata().getName();

        // httpClientCall();
        String command = "/subsystem=logging/root-logger=ROOT:read-attribute(name=level)";
        ModelNode reply = TestHelper.executeCLICommands(client, podName, CONTAINER_NAME, command);
        ModelNode result = TestHelper.checkOperation(true, reply);
        assertEquals("DEBUG", result.asString());
    }

    private void httpClientCall() throws IOException {
        try (LocalPortForward p = client.services().withName(CONTAINER_NAME).portForward(8080)) { //port matches what is configured in properties file
            assertTrue(p.isAlive());
            URL url = new URL("http://localhost:" + p.getLocalPort() + "/");

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().get().url(url)
                    .header("Connection", "close")
                    .build();
            Response response = client.newCall(request).execute();
            assertEquals(response.body().string(), "{\"result\":\"OK\"}");
        }
    }
}
