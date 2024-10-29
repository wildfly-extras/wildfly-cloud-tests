/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.test.cloud.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.dmr.ModelNode;

public class TestHelper {

    @FunctionalInterface
    public interface PortForwardAction<T> {
        T apply(URL url) throws Exception;
    }

    private final KubectlHelper kubectl;
    private final String containerName;

    public TestHelper(KubectlHelper kubectl, String containerName) {
        this.kubectl = kubectl;
        this.containerName = containerName;
    }

    public KubectlHelper kubectl() {
        return kubectl;
    }

    public String getContainerName() {
        return containerName;
    }

    public boolean waitUntilWildFlyIsReady(long delay) {
        long start = System.currentTimeMillis();
        String podName = getFirstPodName();
        int localPort = findFreePort();
        System.out.println("[readiness] Port-forwarding pod/" + podName + " port 9990 -> localhost:" + localPort);
        AtomicReference<Process> portForward = new AtomicReference<>(
                kubectl.portForward("pod/" + podName, localPort, 9990));
        AtomicBoolean stopStderr = new AtomicBoolean(false);
        Thread fwdStderr = new Thread(() -> {
            while (!stopStderr.get()) {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(portForward.get().getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("[port-forward] " + line);
                    }
                } catch (Exception ignored) {}
                if (!stopStderr.get()) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                }
            }
        });
        fwdStderr.setDaemon(true);
        fwdStderr.start();
        try {
            Thread.sleep(2000);
            int attempt = 0;
            while (System.currentTimeMillis() - start < delay) {
                attempt++;
                if (!portForward.get().isAlive()) {
                    System.out.println("[readiness] Attempt " + attempt +
                            ": port-forward process died (exit=" + portForward.get().exitValue() + "), restarting");
                    portForward.set(kubectl.portForward("pod/" + podName, localPort, 9990));
                    Thread.sleep(2000);
                    continue;
                }
                try {
                    URL url = new URL("http://localhost:" + localPort + "/health/ready");
                    HttpClient client = HttpClient.newBuilder().build();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(url.toURI()).GET().build();
                    HttpResponse<String> response = client.send(request,
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        String log = kubectl.getPodLog(podName);
                        if (log.contains("WFLYSRV0025")) {
                            System.out.println("[readiness] Ready after " + attempt + " attempts");
                            return true;
                        }
                        System.out.println("[readiness] Attempt " + attempt +
                                ": health=200 but WFLYSRV0025 not in log yet");
                    } else {
                        System.out.println("[readiness] Attempt " + attempt +
                                ": health=" + response.statusCode() +
                                " body=" + response.body());
                    }
                } catch (Exception e) {
                    System.out.println("[readiness] Attempt " + attempt +
                            ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stopStderr.set(true);
            portForward.get().destroyForcibly();
        }
        return false;
    }

    public ModelNode executeCLICommands(String... commands) {
        String bashCmd = String.format(
                "$JBOSS_HOME/bin/jboss-cli.sh -c --commands=\"%s\"",
                Arrays.stream(commands)
                        .map(TestHelper::escapeCommand)
                        .collect(Collectors.joining(",")));
        String podName = getFirstPodName();
        String output = kubectl.exec(podName, containerName, "bash", "-c", bashCmd);
        assertTrue(output != null && !output.trim().isEmpty(),
                "No output from CLI command. Full command: " + bashCmd);
        return ModelNode.fromString(output);
    }

    public String readFile(String filePath) {
        String podName = getFirstPodName();
        return kubectl.exec(podName, containerName, "bash", "-c", "cat " + filePath);
    }

    public String runCommand(String bashCommand, boolean expectOutput) {
        String podName = getFirstPodName();
        String output = kubectl.exec(podName, containerName, "bash", "-c", bashCommand);
        if (expectOutput && (output == null || output.trim().isEmpty())) {
            throw new IllegalStateException(
                    "No output from command. Full command: " + bashCommand);
        }
        return output;
    }

    public <R> R doWithWebPortForward(String path, PortForwardAction<R> action) throws Exception {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String podName = getFirstPodName();
        int localPort = findFreePort();
        Process portForward = kubectl.portForward("pod/" + podName, localPort, 8080);
        try {
            Thread.sleep(1000);
            URL url = new URL("http://localhost:" + localPort + path);
            return action.apply(url);
        } finally {
            portForward.destroyForcibly();
        }
    }

    public Map<String, String> getAllPodLogs() {
        Map<String, String> logs = new LinkedHashMap<>();
        List<String> podNames = kubectl.getAllPodNames();
        for (String podName : podNames) {
            try {
                logs.put(podName, kubectl.getPodLog(podName));
            } catch (Exception e) {
                logs.put(podName, "Failed to get log: " + e.getMessage());
            }
        }
        return logs;
    }

    public String getPodLog(String podName) {
        return kubectl.getPodLog(podName);
    }

    private String getFirstPodName() {
        List<String> pods = kubectl.getPodNames(
                "app.kubernetes.io/name=" + containerName);
        if (pods.isEmpty()) {
            throw new RuntimeException(
                    "No pods found with label app.kubernetes.io/name=" + containerName);
        }
        return pods.get(0);
    }

    private static String escapeCommand(String cmd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if (c == '$' && (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\\')) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException("Could not find a free port", e);
        }
    }

    public static ModelNode checkAndGetResult(ModelNode result) {
        assertTrue("success".equals(result.get("outcome").asString()), result.asString());
        return result.get("result");
    }

    public static void checkFailed(ModelNode result) {
        assertTrue("failed".equals(result.get("outcome").asString()));
    }
}
