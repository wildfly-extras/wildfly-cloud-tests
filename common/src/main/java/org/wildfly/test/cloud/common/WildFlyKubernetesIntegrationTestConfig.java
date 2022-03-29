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

import io.dekorate.testing.config.KubernetesIntegrationTestConfig;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class WildFlyKubernetesIntegrationTestConfig extends KubernetesIntegrationTestConfig {

    private final String namespace;

    private WildFlyKubernetesIntegrationTestConfig(boolean deployEnabled, boolean buildEnabled, long readinessTimeout,
                                                   String[] additionalModules, String namespace) {
        super(deployEnabled, buildEnabled, readinessTimeout, additionalModules);
        this.namespace = namespace;
    }


    static WildFlyKubernetesIntegrationTestConfig adapt(WildFlyKubernetesIntegrationTest annotation) {
        return new WildFlyKubernetesIntegrationTestConfig(
                annotation.deployEnabled(),
                annotation.buildEnabled(),
                annotation.readinessTimeout(),
                annotation.additionalModules(),
                annotation.namespace());
    }

    public String getNamespace() {
        return namespace;
    }

}
