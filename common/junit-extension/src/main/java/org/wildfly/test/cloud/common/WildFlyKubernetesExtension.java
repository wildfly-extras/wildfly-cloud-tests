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

import io.dekorate.testing.kubernetes.KubernetesExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.wildfly.test.cloud.common.WildFlyCommonExtension.WILDFLY_STORE;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WildFlyKubernetesExtension extends KubernetesExtension {
    private static final String TEST_CONFIG = "integration-test-config";

    private final WildFlyCommonExtension delegate = WildFlyCommonExtension.createForKubernetes();

    @Override
    public WildFlyKubernetesIntegrationTestConfig getKubernetesIntegrationTestConfig(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(WILDFLY_STORE);
        WildFlyKubernetesIntegrationTestConfig cfg =
                store.get(TEST_CONFIG, WildFlyKubernetesIntegrationTestConfig.class);
        if (cfg != null) {
            return cfg;
        }
        // Override the super class method so we can use our own configuration
        cfg = context.getElement()
                .map(e -> WildFlyKubernetesIntegrationTestConfig.adapt(e.getAnnotation(WildFlyKubernetesIntegrationTest.class)))
                .orElseThrow(
                        () -> new IllegalStateException("Test class not annotated with @" + WildFlyKubernetesIntegrationTest.class.getSimpleName()));

        store.put(TEST_CONFIG, cfg);
        return cfg;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        delegate.beforeAll(getKubernetesIntegrationTestConfig(context), context);
        super.beforeAll(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        delegate.dumpPodInformation(context);
        super.afterAll(context);
        delegate.afterAll(getKubernetesIntegrationTestConfig(context), context);
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        super.postProcessTestInstance(testInstance, context);
        delegate.postProcessTestInstance(testInstance, context, () -> getName(context));
    }
}
