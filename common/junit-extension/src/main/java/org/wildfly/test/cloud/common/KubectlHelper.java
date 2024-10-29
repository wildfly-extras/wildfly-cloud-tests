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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class KubectlHelper {

    private final String namespace;

    public KubectlHelper(String namespace) {
        this.namespace = namespace;
    }

    public List<String> getPodNames(String labelSelector) {
        String output = runKubectl("get", "pods", "-l", labelSelector, "-o", "jsonpath={.items[*].metadata.name}");
        List<String> names = new ArrayList<>();
        for (String name : output.trim().split("\\s+")) {
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    public List<String> getAllPodNames() {
        String output = runKubectl("get", "pods", "-o", "jsonpath={.items[*].metadata.name}");
        List<String> names = new ArrayList<>();
        for (String name : output.trim().split("\\s+")) {
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    public void deletePod(String podName) {
        runKubectl("delete", "pod", podName);
    }

    public String getPodLog(String podName) {
        return runKubectl("logs", podName);
    }

    public Process streamPodLog(String podName) {
        return startKubectl("logs", "-f", podName);
    }

    public void scaleDeployment(String name, int replicas) {
        runKubectl("scale", "deployment", name, "--replicas=" + replicas);
    }

    public int getNodePort(String serviceName, String portName) {
        String jsonpath = String.format(
                "{.spec.ports[?(@.name==\"%s\")].nodePort}", portName);
        String output = runKubectl("get", "svc", serviceName, "-o", "jsonpath=" + jsonpath);
        return Integer.parseInt(output.trim());
    }

    public String getClusterIP() {
        try {
            ProcessBuilder pb = new ProcessBuilder("minikube", "ip");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readOutput(process);
            int exitCode = process.waitFor();
            if (exitCode == 0 && !output.trim().isEmpty()) {
                return output.trim();
            }
        } catch (Exception e) {
            // fall through to kubectl
        }
        String output = runKubectl("cluster-info");
        for (String line : output.split("\n")) {
            if (line.contains("control plane") || line.contains("master")) {
                String url = line.replaceAll("\\x1B\\[[;\\d]*m", ""); // strip ANSI
                int start = url.indexOf("://") + 3;
                int end = url.indexOf(":", start);
                if (start > 2 && end > start) {
                    return url.substring(start, end);
                }
            }
        }
        throw new RuntimeException("Could not determine cluster IP");
    }

    public String exec(String podName, String container, String... command) {
        List<String> args = new ArrayList<>();
        args.add("exec");
        args.add(podName);
        if (container != null) {
            args.add("-c");
            args.add(container);
        }
        args.add("--");
        for (String c : command) {
            args.add(c);
        }
        return runKubectl(args.toArray(new String[0]));
    }

    public String run(String... args) {
        return runKubectl(args);
    }

    public Process portForward(String target, int localPort, int remotePort) {
        return startKubectl("port-forward", target, localPort + ":" + remotePort);
    }

    private String runKubectl(String... args) {
        Process process = startKubectl(args);
        try {
            StringBuilder stderrBuilder = new StringBuilder();
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stderrBuilder.length() > 0) stderrBuilder.append("\n");
                        stderrBuilder.append(line);
                    }
                } catch (IOException ignored) {}
            });
            stderrThread.setDaemon(true);
            stderrThread.start();
            String output = readOutput(process);
            int exitCode = process.waitFor();
            stderrThread.join(5000);
            if (exitCode != 0) {
                throw new RuntimeException(
                        "kubectl command failed (exit " + exitCode + "): " + stderrBuilder);
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("kubectl command interrupted", e);
        }
    }

    private Process startKubectl(String... args) {
        List<String> command = new ArrayList<>();
        command.add("kubectl");
        if (namespace != null) {
            command.add("-n");
            command.add(namespace);
        }
        for (String arg : args) {
            command.add(arg);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            return pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start kubectl: " + e.getMessage(), e);
        }
    }

    private String readOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read kubectl output", e);
        }
    }
}
