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

import org.junit.jupiter.api.extension.ExtensionContext;

import io.dekorate.testing.openshift.OpenshiftExtension;
import io.dekorate.testing.openshift.config.OpenshiftIntegrationTestConfig;

public class WildFlyOpenshiftExtension extends OpenshiftExtension {

    private final WildFlyCommonExtension delegate = WildFlyCommonExtension.createForOpenshift();

    public WildFlyOpenshiftIntegrationTestConfig getIntegrationTestConfig(ExtensionContext context) {
        // Override the super class method so we can use our own configuration
        return context.getElement()
                .map(e -> WildFlyOpenshiftIntegrationTestConfig.adapt(e.getAnnotation(WildFlyOpenshiftIntegrationTest.class)))
                .orElseThrow(
                        () -> new IllegalStateException("Test class not annotated with @" + WildFlyOpenshiftIntegrationTestConfig.class.getSimpleName()));
    }

    private static final ExtensionContext.Namespace WILDFLY_STORE = ExtensionContext.Namespace.create("org", "wildfly", "test");
    private static final String KUBERNETES_CONFIG_DATA = "kubernetes-config";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        delegate.beforeAll(getIntegrationTestConfig(context), context);
        super.beforeAll(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        super.afterAll(context);
        delegate.afterAll(getIntegrationTestConfig(context), context);
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        super.postProcessTestInstance(testInstance, context);
        delegate.postProcessTestInstance(testInstance, context, () -> getName(context));
    }

    @Override
    public OpenshiftIntegrationTestConfig getOpenshiftIntegrationTestConfig(ExtensionContext context) {
        return getIntegrationTestConfig(context);
    }
}
