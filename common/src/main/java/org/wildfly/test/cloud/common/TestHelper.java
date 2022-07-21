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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Utility to interact with an application running in a container.
 * It can be injected into your test, in which case it is pre-initialised
 * with the container name for your application. Alternatively, you can call its
 * static methods and specify the container and pod names.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestHelper {
    private KubernetesClient k8sClient;
    private String containerName;

    TestHelper(KubernetesClient k8sClient, String containerName) {
        this.k8sClient = k8sClient;
        this.containerName = containerName;
    }

    public String getContainerName() {
        return containerName;
    }

    public boolean waitUntilWildFlyIsReady(long delay) {
        return waitUntilWildFlyIsReady(k8sClient, getFirstPodName(), containerName, delay);
    }

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

    public ModelNode executeCLICommands(String... commands) {
        return executeCLICommands(k8sClient, getFirstPodName(), containerName, commands);
    }

    public static ModelNode executeCLICommands(KubernetesClient client, String podName, String containerName, String... commands) {
        String bashCmd = String.format(
                "$JBOSS_HOME/bin/jboss-cli.sh  -c --commands=\"%s\"",
                Arrays
                        .stream(commands)
                        .map(cmd -> escapeCommand(cmd))
                        .collect(Collectors.joining(",")));
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
                    public void onFailure(Throwable throwable, Response response) {
                        errorDuringExecution.set(true);
                        execLatch.countDown();
                    }

                    @Override
                    public void onClose(int i, String s) {
                        execLatch.countDown();
                    }
                }).exec( "bash", "-c", bashCmd);
        try {
            boolean ok = execLatch.await(10, TimeUnit.SECONDS);
            assertTrue(ok, "CLI Commands timed out");
            assertFalse(errorDuringExecution.get());
        } catch (InterruptedException e) {
        }

        String output = out.toString();
        if (output.trim().length() == 0) {
            throw new IllegalStateException("No output was found executing the CLI command. " +
                    "This means an error happened in the bash layer. The full command is:\n" + bashCmd);
        }

        return ModelNode.fromString(out.toString());
    }

    private static String escapeCommand(String cmd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if (c == '$') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\\') {
                    sb.append('\\');
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static ModelNode checkAndGetResult(ModelNode result) {
        assertTrue("success".equals(result.get("outcome").asString()), result.asString());
        return result.get("result");
    }

    public static void checkFailed(ModelNode result) {
        assertTrue("failed".equals(result.get("outcome").asString()));
    }

    public <R> R doWithWebPortForward(String path, ForwardedPortAction<R> action) throws Exception {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // In dekorate they connect directly to the pod instead.
        // Despite the service running on port 80, the below service port forward actually pulls out the first pod
        // and sets up a port-forward to that. That pod listens on port 8080 rather than 80.
        try (LocalPortForward p = k8sClient.services().withName(containerName).portForward(8080)) {
            assertTrue(p.isAlive());
            URL url = new URL("http://localhost:" + p.getLocalPort() + path);
            return action.get(url);
        }
    }
    private String getFirstPodName() {
        Pod pod = k8sClient.pods().withLabel("app.kubernetes.io/name=" + containerName).list().getItems().get(0);
        return pod.getMetadata().getName();
    }
}
