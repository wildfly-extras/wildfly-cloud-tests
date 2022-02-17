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

package org.wildfly.test.cloud.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.jboss.dmr.ModelNode;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestHelper {
    public static boolean waitUntilWildFlyIsReady(KubernetesClient k8sClient, String podName, String containerName, long delay) {
        long start = System.currentTimeMillis();
        long spent = System.currentTimeMillis() - start;
        while (spent < delay) {
            try (LocalPortForward p = k8sClient.services().withName(containerName).portForward(9990)) { //port matches what is configured in properties file
                assertTrue(p.isAlive());
                URL url = new URL("http://localhost:" + p.getLocalPort() + "/health/ready");

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().get().url(url)
                        .header("Connection", "close")
                        .build();
                Response response = client.newCall(request).execute();
                if (response.code() == 200) {
                    String log = k8sClient.pods().withName(podName).inContainer(containerName).getLog();
                    if (log.contains("WFLYSRV0025")) {
                        System.out.println("Returning true");
                        return true;
                    }
                }
            } catch (IOException e) {
                // Might happen if the container is not up yet
            }
            spent = System.currentTimeMillis() - start;
            if (spent < delay) {
                try {
                    Thread.sleep(delay / 10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.interrupted();
                    break;
                }
            }
        }
        return false;
    }


    public static ModelNode executeCLICommands(KubernetesClient client, String podName, String containerName, String... commands) {
        String bashCmdTemplate = String.format("$JBOSS_HOME/bin/jboss-cli.sh  -c --commands=\"%s\"", Arrays.stream(commands).collect(Collectors.joining(",")));
        final CountDownLatch execLatch = new CountDownLatch(1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicBoolean errorDuringExecution = new AtomicBoolean(false);
        client.pods().withName(podName).inContainer(containerName)
                //.readingInput(System.in)
                .writingOutput(out)
                .writingError(System.err)
                //..withTTY()
                .usingListener(new ExecListener() {
                    @Override
                    public void onOpen(Response response) {
                    }

                    @Override
                    public void onFailure(Throwable throwable, Response response) {
                        errorDuringExecution.set(true);
                        execLatch.countDown();
                    }

                    @Override
                    public void onClose(int i, String s) {
                        execLatch.countDown();
                    }
                }).exec( "bash", "-c", bashCmdTemplate);
        try {
            boolean ok = execLatch.await(10, TimeUnit.SECONDS);
            assertTrue(ok, "CLI Commands timed out");
            assertFalse(errorDuringExecution.get());
        } catch (InterruptedException e) {
        }
        ModelNode result = ModelNode.fromString(out.toString());
        return result;
    }

    public static ModelNode checkOperation(boolean mustSucceed, ModelNode result) {
        assertEquals(mustSucceed, "success".equals(result.get("outcome").asString()));
        return result.get("result");
    }
}
