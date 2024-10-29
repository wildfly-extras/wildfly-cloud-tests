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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.TestWatcher;

public class WildFlyKubernetesExtension
        implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor, TestWatcher {

    private static final ExtensionContext.Namespace EXT_NS =
            ExtensionContext.Namespace.create(WildFlyKubernetesExtension.class);
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String modulePath = resolveModulePath();
        String projectRoot = resolveProjectRoot();

        String scriptPath = projectRoot + "/.github/scripts/setup-test.sh";
        runScript(scriptPath, modulePath);

        // Read state file written by setup-test.sh
        File stateFile = new File(projectRoot, modulePath + "/target/.cloud-test-state");
        Properties state = new Properties();
        if (stateFile.exists()) {
            try (FileInputStream fis = new FileInputStream(stateFile)) {
                state.load(fis);
            }
        }

        String appName = state.getProperty("APP_NAME", "");
        String namespace = state.getProperty("NAMESPACE", "");

        context.getStore(EXT_NS).put("appName", appName);
        context.getStore(EXT_NS).put("namespace", namespace);
        context.getStore(EXT_NS).put("modulePath", modulePath);

        // One-time readiness check after deployment
        KubectlHelper kubectl = new KubectlHelper(
                namespace.isEmpty() ? null : namespace);
        TestHelper helper = new TestHelper(kubectl, appName);
        long readyTimeout = Long.parseLong(
                System.getenv().getOrDefault("WILDFLY_READINESS_TIMEOUT", "120000"));
        boolean ready = helper.waitUntilWildFlyIsReady(readyTimeout);
        if (!ready) {
            dumpDiagnostics(kubectl, appName);
            throw new RuntimeException(
                    "WildFly did not become ready within " + (readyTimeout / 1000) + "s");
        }
        context.getStore(EXT_NS).put("helper", helper);
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context)
            throws Exception {
        if (testInstance instanceof WildFlyCloudTestCase) {
            TestHelper helper = (TestHelper) context.getStore(EXT_NS).get("helper");
            ((WildFlyCloudTestCase) testInstance).setHelper(helper);
        }
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        // testFailed receives method-level context; helper is in class-level store
        ExtensionContext classContext = context.getParent().orElse(context);
        TestHelper helper = (TestHelper) classContext.getStore(EXT_NS).get("helper");
        if (helper == null) return;

        System.out.println("\n==============================================================");
        System.out.println("  Outputting full pod logs...");
        System.out.println("==============================================================\n");
        Map<String, String> logs = helper.getAllPodLogs();
        for (Map.Entry<String, String> entry : logs.entrySet()) {
            System.out.println("==============> LOGS FOR POD: " + entry.getKey() + " <==================\n");
            System.out.println(entry.getValue());
            System.out.println("==============> END LOGS: " + entry.getKey() + " <=======================\n\n");
        }

        try {
            System.out.println("\n==============================================================");
            System.out.println("  standalone.xml contents");
            System.out.println("==============================================================\n");
            String xml = helper.readFile("$JBOSS_HOME/standalone/configuration/standalone.xml");
            System.out.println(xml);
            System.out.println("==============> END standalone.xml <==========================\n\n");
        } catch (Exception e) {
            System.err.println("Could not dump standalone.xml: " + e.getMessage());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        String modulePath = (String) context.getStore(EXT_NS).get("modulePath");
        if (modulePath == null) {
            modulePath = resolveModulePath();
        }
        String projectRoot = resolveProjectRoot();

        String scriptPath = projectRoot + "/.github/scripts/teardown-test.sh";
        runScript(scriptPath, modulePath);
    }

    private void dumpDiagnostics(KubectlHelper kubectl, String appName) {
        System.out.println("\n==============================================================");
        System.out.println("  DIAGNOSTICS: WildFly readiness check failed for " + appName);
        System.out.println("==============================================================\n");
        try {
            System.out.println("--- kubectl get pods -o wide ---");
            System.out.println(kubectl.run("get", "pods", "-o", "wide"));
        } catch (Exception e) {
            System.out.println("(failed: " + e.getMessage() + ")");
        }
        List<String> pods = kubectl.getPodNames("app.kubernetes.io/name=" + appName);
        for (String pod : pods) {
            try {
                System.out.println("\n--- kubectl describe pod " + pod + " ---");
                System.out.println(kubectl.run("describe", "pod", pod));
            } catch (Exception e) {
                System.out.println("(failed: " + e.getMessage() + ")");
            }
            try {
                System.out.println("\n--- kubectl logs " + pod + " ---");
                System.out.println(kubectl.getPodLog(pod));
            } catch (Exception e) {
                System.out.println("(failed: " + e.getMessage() + ")");
            }
        }
        try {
            System.out.println("\n--- Resource stats ---");
            ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                    "df -h / | tail -1 | awk '{print \"Disk: \"$3\" used / \"$4\" avail (\"$5\" used)\"}'; " +
                    "free -h 2>/dev/null | awk '/^Mem:/{print \"Memory: \"$3\" used / \"$7\" avail / \"$2\" total\"}' || true");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                r.lines().forEach(System.out::println);
            }
            p.waitFor();
        } catch (Exception e) {
            System.out.println("(failed: " + e.getMessage() + ")");
        }
        System.out.println("\n==============================================================");
        System.out.println("  END DIAGNOSTICS");
        System.out.println("==============================================================\n");
    }

    private void runScript(String scriptPath, String modulePath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", scriptPath, modulePath);
        pb.redirectErrorStream(true);
        pb.directory(new File(resolveProjectRoot()));
        Process process = pb.start();
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[setup] " + line);
                }
            } catch (IOException e) {
                // process ended
            }
        });
        outputThread.setDaemon(true);
        outputThread.start();
        int exitCode = process.waitFor();
        outputThread.join(5000);
        if (exitCode != 0) {
            throw new RuntimeException(
                    "Script " + scriptPath + " failed with exit code " + exitCode);
        }
    }

    static String resolveModulePath() {
        // Maven sets user.dir to the module directory during test execution
        String userDir = System.getProperty("user.dir");
        String projectRoot = resolveProjectRoot();
        Path modulePath = Path.of(projectRoot).relativize(Path.of(userDir));
        return modulePath.toString();
    }

    static String resolveProjectRoot() {
        // Walk up from user.dir looking for .cloud-tests-root-marker
        File dir = new File(System.getProperty("user.dir"));
        while (dir != null) {
            if (new File(dir, ".cloud-tests-root-marker").exists()) {
                return dir.getAbsolutePath();
            }
            dir = dir.getParentFile();
        }
        throw new RuntimeException(
                "Could not find project root (.cloud-tests-root-marker not found)");
    }
}
