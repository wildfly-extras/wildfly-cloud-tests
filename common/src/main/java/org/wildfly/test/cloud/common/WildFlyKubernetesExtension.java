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

import static io.dekorate.testing.Testing.DEKORATE_STORE;

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.dekorate.DekorateException;
import io.dekorate.testing.kubernetes.KubernetesExtension;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WildFlyKubernetesExtension extends KubernetesExtension {


    @Override
    public WildFlyKubernetesIntegrationTestConfig getKubernetesIntegrationTestConfig(ExtensionContext context) {
        // Override the super class method so we can use our own configuration
        return context.getElement()
                .map(e -> WildFlyKubernetesIntegrationTestConfig.adapt(e.getAnnotation(WildFlyKubernetesIntegrationTest.class)))
                .orElseThrow(
                        () -> new IllegalStateException("Test class not annotated with @" + WildFlyKubernetesIntegrationTest.class.getSimpleName()));
    }

    private static final ExtensionContext.Namespace WILDFLY_STORE = ExtensionContext.Namespace.create("org", "wildfly", "test");
    private static final String KUBERNETES_CONFIG_DATA = "kubernetes-config";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        WildFlyKubernetesIntegrationTestConfig config = getKubernetesIntegrationTestConfig(context);
        WildFlyTestContext testContext = initTestContext(context, config);
        if (config != null) {
            setNamespace(context, config, testContext);
        }
        super.beforeAll(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        super.afterAll(context);

        WildFlyKubernetesIntegrationTestConfig config = getKubernetesIntegrationTestConfig(context);
        WildFlyTestContext testContext = context.getStore(WILDFLY_STORE).get(KUBERNETES_CONFIG_DATA, WildFlyTestContext.class);
        if (testContext != null) {
            deleteNamespace(context, config, testContext);
        }
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {

        super.postProcessTestInstance(testInstance, context);

        // Inject the TestHelper
        String projectName = super.getName(context);
        TestHelper helper = new TestHelper(
                super.getKubernetesClient(context),
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

    private WildFlyTestContext initTestContext(ExtensionContext context, WildFlyKubernetesIntegrationTestConfig config) {
        if (config != null) {
            WildFlyTestContext testContext = new WildFlyTestContext();
            ExtensionContext.Store store = context.getStore(WILDFLY_STORE);
            store.put(KUBERNETES_CONFIG_DATA, testContext);
            return testContext;
        }
        return null;
    }

    private void setNamespace(ExtensionContext context, WildFlyKubernetesIntegrationTestConfig config, WildFlyTestContext testContext) throws Exception {
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

            // Switch the fabric8 client to the namespaced one
            Assertions.assertInstanceOf(HttpClientAware.class, client);
            Config namespaceConfig = new ConfigBuilder(client.getConfiguration()).withNamespace(config.getNamespace()).build();
            KubernetesClient namespacedClient = new DefaultKubernetesClient(((HttpClientAware)client).getHttpClient(), namespaceConfig);
            testContext.setOriginalClient(client);
            setKubernetesClientInContext(context, namespacedClient);
        }
    }

    private void deleteNamespace(ExtensionContext context, WildFlyKubernetesIntegrationTestConfig config, WildFlyTestContext testContext) {
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
                if (e instanceof RuntimeException) {
                    throw (RuntimeException)e;
                }
                throw new RuntimeException(e);
            }
        }
    }

    private void setKubernetesClientInContext(ExtensionContext context, KubernetesClient client) {
        context.getStore(DEKORATE_STORE).put(KUBERNETES_CLIENT, client);
    }

    private WildFlyTestContext getWildFlyTestContext(ExtensionContext context) {
        return context.getStore(WILDFLY_STORE).get(KUBERNETES_CONFIG_DATA, WildFlyTestContext.class);
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
            Assertions.assertInstanceOf(HttpClientAware.class, client);
            Config namespaceConfig = new ConfigBuilder(client.getConfiguration()).withNamespace(namespace).build();
            KubernetesClient namespacedClient = new DefaultKubernetesClient(((HttpClientAware)client).getHttpClient(), namespaceConfig);
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
            ProcessBuilder pb = new ProcessBuilder("kubectl", "config", "set-context", "--current", "--namespace=" + namespace);
            Process process = pb.start();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("Error changing namespace to " + namespace);
            }


        }
    }
}
