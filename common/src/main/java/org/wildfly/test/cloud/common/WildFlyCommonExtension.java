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

import io.dekorate.DekorateException;
import io.dekorate.testing.WithDiagnostics;
import io.dekorate.testing.WithKubernetesClient;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.impl.KubernetesClientImpl;
import io.fabric8.kubernetes.client.readiness.Readiness;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.dekorate.testing.Testing.DEKORATE_STORE;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
abstract class WildFlyCommonExtension implements WithDiagnostics, WithKubernetesClient {

    private final ExtensionType extensionType;

    private WildFlyCommonExtension(ExtensionType extensionType) {
        this.extensionType = extensionType;
    }

    static WildFlyCommonExtension createForKubernetes() {
        return new WildFlyCommonExtension.Kubernetes();
    }

    static WildFlyCommonExtension createForOpenshift() {
        return new WildFlyCommonExtension.Openshift();
    }

    public WildFlyIntegrationTestConfig getIntegrationTestConfig(ExtensionContext context) {
        // Override the super class method so we can use our own configuration
        return context.getElement()
                .map(e -> WildFlyKubernetesIntegrationTestConfig.adapt(e.getAnnotation(WildFlyKubernetesIntegrationTest.class)))
                .orElseThrow(
                        () -> new IllegalStateException("Test class not annotated with @" + WildFlyKubernetesIntegrationTest.class.getSimpleName()));
    }

    private static final ExtensionContext.Namespace WILDFLY_STORE = ExtensionContext.Namespace.create("org", "wildfly", "test");
    private static final String KUBERNETES_CONFIG_DATA = "kubernetes-config";

    public void beforeAll(WildFlyIntegrationTestConfig config, ExtensionContext context) throws Exception {
        WildFlyTestContext testContext = initTestContext(context, config);
        if (config != null) {
            setNamespace(context, config, testContext);

            ExtraTestSetup extraTestSetup = config.getExtraTestSetup();
            config.addAdditionalKubernetesResources(extraTestSetup.beforeAll(context));

            deployKubernetesResources(context, config, testContext);
        }

        backupAndReplacePlaceholdersInKubernetesYaml(context, config);
    }

    public void afterAll(WildFlyIntegrationTestConfig config, ExtensionContext context) {
        WildFlyTestContext testContext = context.getStore(WILDFLY_STORE).get(KUBERNETES_CONFIG_DATA, WildFlyTestContext.class);
        boolean error = false;
        if (testContext != null) {
            try {
                cleanupKubernetesResources(context, config, testContext);
            } catch (Throwable t) {
                t.printStackTrace();
                error = true;
            } finally {
                deleteNamespace(context, config, testContext);
            }
            if (error) {
                Assertions.fail("Errors occurred cleaning up the test, see the logs for details");
            }
        }
        if (Files.exists(extensionType.backup)) {
            try {
                Files.delete(extensionType.backup);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void postProcessTestInstance(Object testInstance, ExtensionContext context, Supplier<String> nameSupplier) {
        // Inject the TestHelper
        String projectName = nameSupplier.get();
        TestHelper helper = new TestHelper(
                getKubernetesClient(context),
                projectName);

        Class clazz = testInstance.getClass();
        while (clazz != Object.class) {
            Arrays.stream(clazz.getDeclaredFields())
                    .forEach(field -> {
                        if (!field.getType().isAssignableFrom(TestHelper.class)) {
                            return;
                        }

                        //This is to make sure we don't write on fields by accident.
                        //Note: we don't require the exact annotation. Any annotation named Inject will do (be it javax, guice etc)
                        if (!Arrays.stream(field.getDeclaredAnnotations()).filter(a -> a.annotationType().getSimpleName().equalsIgnoreCase("Inject"))
                                .findAny().isPresent()) {
                            return;
                        }

                        field.setAccessible(true);
                        try {
                            field.set(testInstance, helper);
                        } catch (IllegalAccessException e) {
                            throw DekorateException.launderThrowable(e);
                        }

                    });
            clazz = clazz.getSuperclass();
        }
    }

    private WildFlyTestContext initTestContext(ExtensionContext context, WildFlyIntegrationTestConfig config) {
        if (config != null) {
            WildFlyTestContext testContext = new WildFlyTestContext();
            ExtensionContext.Store store = context.getStore(WILDFLY_STORE);
            store.put(KUBERNETES_CONFIG_DATA, testContext);
            return testContext;
        }
        return null;
    }

    // Different for Kubernetes and OpenShift
    protected abstract void setNamespace(ExtensionContext context, WildFlyIntegrationTestConfig config, WildFlyTestContext testContext) throws Exception;

    // Different for Kubernetes and OpenShift
    protected abstract void deleteNamespace(ExtensionContext context, WildFlyIntegrationTestConfig config, WildFlyTestContext testContext);

    private void deployKubernetesResources(ExtensionContext context, WildFlyIntegrationTestConfig config, WildFlyTestContext testContext) {
        if (config.getKubernetesResources().isEmpty()) {
            return;
        }
        for (KubernetesResource kubernetesResource : config.getKubernetesResources()) {
            final KubernetesList resourceList;
            try {
                try (InputStream in = getLocalOrRemoteKubernetesResourceInputStream(kubernetesResource.definitionLocation())) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    StringBuilder replacedInput = new StringBuilder();
                    String line = reader.readLine();
                    while (line != null) {
                        line = performConfigPlaceholderReplacementForLine(context, config, line);

                        replacedInput.append(line);
                        replacedInput.append("\n");
                        line = reader.readLine();
                    }

                    resourceList = WildFlySerialization.unmarshalAsList(new ByteArrayInputStream(replacedInput.toString().getBytes(StandardCharsets.UTF_8)));
                }
            } catch (Exception e) {
                throw toRuntimeException(e);
            }
            startResourcesInList(context, kubernetesResource, resourceList);
        }
    }

    private String performConfigPlaceholderReplacementForLine(ExtensionContext context, WildFlyIntegrationTestConfig config, String line) {
        for (Map.Entry<String, ConfigPlaceholderReplacer> replacement : config.getPlaceholderReplacements().entrySet()) {
            String placeholder = replacement.getKey();
            ConfigPlaceholderReplacer replacer = replacement.getValue();
            if (line.contains(placeholder)) {
                line = replacer.replace(context, placeholder, line);
            }
        }
        return line;
    }

    private void startResourcesInList(ExtensionContext context, KubernetesResource kubernetesResource, KubernetesList resourceList) {
        KubernetesClient client = getKubernetesClient(context);
        resourceList.getItems().stream()
                .forEach(i -> {
                    client.resourceList(i).createOrReplace();
                    System.out.println("Created: " + i.getKind() + " name:" + i.getMetadata().getName() + ".");
                });

        List<HasMetadata> waitables = resourceList.getItems().stream().filter(i -> i instanceof Deployment ||
                i instanceof Pod ||
                i instanceof ReplicaSet ||
                i instanceof ReplicationController).collect(Collectors.toList());
        long started = System.currentTimeMillis();
        System.out.println("Waiting until ready (" + kubernetesResource.readinessTimeout() + " ms)...");
        try {
            waitUntilCondition(context, waitables, i -> Readiness.getInstance().isReady(i), kubernetesResource.readinessTimeout(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Gave up waiting after " + kubernetesResource.readinessTimeout());
        }
        long ended = System.currentTimeMillis();
        System.out.println("Waited: " + (ended - started) + " ms.");
        //Display the item status
        waitables.stream().map(r -> client.resource(r).fromServer().get())
                .forEach(i -> {
                    if (!Readiness.getInstance().isReady(i)) {
                        readinessFailed(context);
                        System.out.println(i.getKind() + ":" + i.getMetadata().getName() + " not ready!");
                    }
                });


        if (hasReadinessFailed(context)) {
            throw new IllegalStateException("Readiness Failed");
        } else if (kubernetesResource.additionalResourcesCreated().length > 0) {
            long end = started + kubernetesResource.readinessTimeout();
            Map<String, ResourceGetter> resourceGetters = new HashMap<>();
            for (org.wildfly.test.cloud.common.Resource resource : kubernetesResource.additionalResourcesCreated()) {
                if (resourceGetters.put(resource.name(), ResourceGetter.create(client, resource)) != null) {
                    throw new IllegalStateException(resource.name() + " appears more than once in additionalResourcesCreated()");
                }
            }

            Map<String, HasMetadata> additionalWaitables = new HashMap<>();
            while (System.currentTimeMillis() < end) {
                for (Map.Entry<String, ResourceGetter> entry : resourceGetters.entrySet()) {
                    if (!additionalWaitables.containsKey(entry.getKey())) {
                        ResourceGetter getter = entry.getValue();
                        HasMetadata hasMetadata = getter.getResource();
                        if (hasMetadata != null) {
                            additionalWaitables.put(entry.getKey(), hasMetadata);
                        }
                    }
                }
                if (additionalWaitables.size() == resourceGetters.size()) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    throw new IllegalStateException(e);
                }
            }

            if (additionalWaitables.size() != resourceGetters.size()) {
                throw new IllegalStateException("Could not start all items in " + kubernetesResource.readinessTimeout());
            }


            try {
                waitUntilCondition(context, additionalWaitables.values(), i -> Readiness.getInstance().isReady(i), end - System.currentTimeMillis(),
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Gave up waiting after " + (System.currentTimeMillis() - started));
            }

            waitables.stream().map(r -> client.resource(r).fromServer().get())
                    .forEach(i -> {
                        if (!Readiness.getInstance().isReady(i)) {
                            readinessFailed(context);
                            System.out.println(i.getKind() + ":" + i.getMetadata().getName() + " not ready!");
                        }
                    });

            if (hasReadinessFailed(context)) {
                throw new IllegalStateException("Readiness Failed");
            }
        }
    }

    private InputStream getLocalOrRemoteKubernetesResourceInputStream(String definitionLocation) throws IOException {
        try {
            URL url = new URL(definitionLocation);
            return new BufferedInputStream(url.openStream());
        } catch (MalformedURLException e) {
            // It is not a valid URL so try resolving locally
            Path path = Path.of(definitionLocation);
            if (!path.isAbsolute()) {
                path = Path.of(".").resolve(path).normalize();
            }
            if (!Files.exists(path)) {
                Assertions.fail(definitionLocation + " resolves to the follwing non-existant location: " + path);
            }
            return new BufferedInputStream(new FileInputStream(path.toFile()));
        }
    }

    private void cleanupKubernetesResources(ExtensionContext context, WildFlyIntegrationTestConfig config, WildFlyTestContext testContext) {
        if (config.getKubernetesResources().isEmpty()) {
            return;
        }

        List<KubernetesResource> kubernetesResources = config.getKubernetesResources();
        for (int i = kubernetesResources.size() - 1 ; i >= 0 ; i--) {
            KubernetesResource kubernetesResource = kubernetesResources.get(i);
            KubernetesList resourceList = null;
            try {
                try (InputStream in = getLocalOrRemoteKubernetesResourceInputStream(kubernetesResource.definitionLocation())) {
                    resourceList = WildFlySerialization.unmarshalAsList(in);
                }
            } catch (Exception e) {
                throw toRuntimeException(e);
            }

            List<HasMetadata> list = resourceList.getItems();
            Collections.reverse(list);
            list.stream().forEach(r -> {
                System.out.println("Deleting: " + r.getKind() + " name:" + r.getMetadata().getName() + ". Deleted:"
                        + getKubernetesClient(context).resource(r).cascading(true).delete());
            });
        }

    }


    protected void setKubernetesClientInContext(ExtensionContext context, KubernetesClient client) {
        context.getStore(DEKORATE_STORE).put(KUBERNETES_CLIENT, client);
    }

    private WildFlyTestContext getWildFlyTestContext(ExtensionContext context) {
        return context.getStore(WILDFLY_STORE).get(KUBERNETES_CONFIG_DATA, WildFlyTestContext.class);
    }

    private void backupAndReplacePlaceholdersInKubernetesYaml(ExtensionContext extensionContext, WildFlyIntegrationTestConfig testConfig) throws IOException {
        // Back
        if (!Files.exists(extensionType.yaml)) {
            return;
        }

        Files.copy(extensionType.yaml, extensionType.backup);

        List<String> lines = Files.readAllLines(extensionType.yaml, StandardCharsets.UTF_8);
        List<String> replacedLines = new ArrayList<>();
        for (String line : lines) {
            line = performConfigPlaceholderReplacementForLine(extensionContext, testConfig, line);
            replacedLines.add(line);
        }

        Files.delete(extensionType.yaml);
        Files.write(extensionType.yaml, replacedLines);
    }

    @Override
    public String[] getAdditionalModules(ExtensionContext context) {
        return getIntegrationTestConfig(context).getAdditionalModules();
    }


    private static class WildFlyTestContext {
        private String useNamespace;
        private boolean createdNamespace;
        private KubernetesClient originalClient;

        public String getUseNamespace() {
            return useNamespace;
        }

        public void setUseNamespace(String useNamespace) {
            this.useNamespace = useNamespace;
        }

        public boolean isCreatedNamespace() {
            return createdNamespace;
        }

        public void setCreatedNamespace(boolean createdNamespace) {
            this.createdNamespace = createdNamespace;
        }

        public void setOriginalClient(KubernetesClient client) {
            this.originalClient = client;
        }

        public KubernetesClient getOriginalClient() {
            return originalClient;
        }
    }

    private class KubernetesNamespaceSwitcher {
        private final String namespace;

        KubernetesNamespaceSwitcher() {
            this("default");
        }

        public KubernetesNamespaceSwitcher(String namespace) {
            this.namespace = namespace;
        }

        void switchNamespace(ExtensionContext context) throws Exception {
            switchKubeCtlNamespace();

            // Replace the client used by dekorate with one for the namespace
            KubernetesClient client = getKubernetesClient(context);
            Config namespaceConfig = new ConfigBuilder(client.getConfiguration()).withNamespace(namespace).build();
            KubernetesClient namespacedClient = new KubernetesClientImpl(client.getHttpClient(), namespaceConfig);
            getWildFlyTestContext(context).setOriginalClient(client);
            setKubernetesClientInContext(context, namespacedClient);
        }

        public void resetNamespaceToDefault(ExtensionContext context) throws Exception {
            switchKubeCtlNamespace();

            // Switch back to the original client
            KubernetesClient client = getKubernetesClient(context);
            setKubernetesClientInContext(context, getWildFlyTestContext(context).getOriginalClient());
            client.close();
        }

        private void switchKubeCtlNamespace() throws Exception {
            System.out.println("Switching namespace to " + namespace);
            ProcessBuilder pb = new ProcessBuilder(extensionType.cliName, "config", "set-context", "--current", "--namespace=" + namespace);
            Process process = pb.start();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            executor.submit(new StreamReader(process.getInputStream(), System.out));
            executor.submit(new StreamReader(process.getErrorStream(), System.err));

            int exit = process.waitFor();

            if (exit != 0) {
                throw new IllegalStateException("Error changing namespace to " + namespace);
            }
        }
    }

    private static class StreamReader implements Runnable {
        private final BufferedReader in;
        private final PrintStream out;

        public StreamReader(InputStream in, PrintStream out) {
            this.in = new BufferedReader(new InputStreamReader(in));
            this.out = out;
        }

        @Override
        public void run() {
            try {
                String line = in.readLine();
                while (line != null) {
                    out.println(line);
                    line = in.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static RuntimeException toRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        } else if (throwable instanceof Error) {
            throw (Error) throwable;
        } else if (throwable instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        throw new RuntimeException(throwable);
    }

    private List<ExtraTestSetup> loadAdditionalTestSetups() {
        ServiceLoader<ExtraTestSetup> sl = ServiceLoader.load(ExtraTestSetup.class);
        return sl.stream().map(p -> p.get()).collect(Collectors.toList());
    }

    private static abstract class ResourceGetter <T extends HasMetadata> {
        protected final KubernetesClient client;
        protected org.wildfly.test.cloud.common.Resource resource;

        public ResourceGetter(KubernetesClient client, org.wildfly.test.cloud.common.Resource resource) {
            this.client = client;
            this.resource = resource;
        }

        abstract T getResource();

        static ResourceGetter create(KubernetesClient client, org.wildfly.test.cloud.common.Resource resource) {
            switch (resource.type()) {
                case DEPLOYMENT:
                    return new DeploymentGetter(client, resource);
                default:
                    throw new IllegalStateException("Unhandled resource type " + resource.type());
            }
        }
    }

    private static class DeploymentGetter extends ResourceGetter<Deployment> {
        public DeploymentGetter(KubernetesClient client, org.wildfly.test.cloud.common.Resource resource) {
            super(client, resource);
        }

        @Override
        Deployment getResource() {
            return client.apps().deployments()./*inNamespace("default").*/withName(resource.name()).get();
        }
    }

    private static class Kubernetes extends WildFlyCommonExtension {
        public Kubernetes() {
            super(ExtensionType.KUBERNETES);
        }

        protected void setNamespace(ExtensionContext context, WildFlyIntegrationTestConfig config, WildFlyTestContext testContext) throws Exception {
            if (!config.getNamespace().isBlank()) {
                KubernetesClient client = getKubernetesClient(context);
                NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> namespaceOperation = client.namespaces();
                NamespaceList list = namespaceOperation.list();
                boolean foundNs = false;
                for (Namespace namespace : list.getItems()) {
                    if (namespace.getMetadata().getName().equals(config.getNamespace())) {
                        foundNs = true;
                        break;
                    }
                }

                if (!foundNs) {
                    Namespace ns =
                            new NamespaceBuilder()
                                    .withNewMetadata()
                                    .withName(config.getNamespace())
                                    .endMetadata()
                                    .build();
                    System.out.println("Creating namespace " + ns) ;
                    namespaceOperation.create(ns);
                }
                // Switch kubectl and the fabric8 client to the new namespace
                testContext.setCreatedNamespace(!foundNs);

                new KubernetesNamespaceSwitcher(config.getNamespace()).switchNamespace(context);
            }
        }

        protected void deleteNamespace(ExtensionContext context, WildFlyIntegrationTestConfig config, WildFlyTestContext testContext) {
            if (!config.getNamespace().isBlank() && testContext.isCreatedNamespace()) {
                KubernetesClient client = getKubernetesClient(context);
                NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> namespaceOperation = client.namespaces();
                NamespaceList list = namespaceOperation.list();
                for (Namespace namespace : list.getItems()) {
                    if (namespace.getMetadata().getName().equals(config.getNamespace())) {
                        namespaceOperation.delete(namespace);
                    }
                }
                try {
                    new KubernetesNamespaceSwitcher().resetNamespaceToDefault(context);
                } catch (Exception e) {
                    throw toRuntimeException(e);
                }
            }
        }
    }

    private static class Openshift extends WildFlyCommonExtension {
        public Openshift() {
            super(ExtensionType.OPENSHIFT);
        }

        @Override
        protected void setNamespace(ExtensionContext context, WildFlyIntegrationTestConfig config, WildFlyTestContext testContext) throws Exception {
            // The namespace should be  set already when running the tests.
            // See if we can do without this since the
            //      oc config set-context --current --namespace=...
            // used by the KubernetesNamespaceSwitcher.switchNamespace(context)
            // isn't available on the old version of OpenShift installed by
            // the GitHub Action
            /*
            String openshiftProject = System.getProperty("dekorate.docker.group");
            if (openshiftProject == null) {
                throw new IllegalStateException("To run the Openshift tests, you need to specify the Openshift project via the dekorate.docker.group system property!");
            }
            try {
                new KubernetesNamespaceSwitcher(openshiftProject).switchNamespace(context);
            } catch (Exception e) {
                throw toRuntimeException(e);
            }
            */
        }

        @Override
        protected void deleteNamespace(ExtensionContext context, WildFlyIntegrationTestConfig config, WildFlyTestContext testContext) {
            // We probably don't need to switch back to the default here
        }
    }

    enum ExtensionType {
        KUBERNETES("kubernetes", "kubectl"),
        OPENSHIFT("openshift", "oc");

        private final Path yaml;
        private final Path backup;
        private final String cliName;

        ExtensionType(String name, String cliName) {
            String base = "target/classes/META-INF/dekorate/" + name;
            yaml = Paths.get(base + ".yml");
            backup = Paths.get(base + ".bak");
            this.cliName = cliName;
        }
    }
}
