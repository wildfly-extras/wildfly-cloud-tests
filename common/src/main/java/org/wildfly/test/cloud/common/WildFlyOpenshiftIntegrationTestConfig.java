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

import java.util.List;
import java.util.Map;

import io.dekorate.testing.openshift.config.EditableOpenshiftIntegrationTestConfig;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class WildFlyOpenshiftIntegrationTestConfig extends EditableOpenshiftIntegrationTestConfig implements WildFlyIntegrationTestConfig {

    private final WildFlyIntegrationTestConfigDelegate wildFlyIntegrationTestConfigDelegate;


    private WildFlyOpenshiftIntegrationTestConfig(boolean deployEnabled, boolean buildEnabled,
                                                  boolean pushEnabled, long imageStreamTagTimeout,
                                                  long readinessTimeout, String[] additionalModules,
                                                  WildFlyIntegrationTestConfigDelegate wildFlyIntegrationTestConfigDelegate){
        super(deployEnabled, buildEnabled, pushEnabled, imageStreamTagTimeout, readinessTimeout, additionalModules);
        this.wildFlyIntegrationTestConfigDelegate = wildFlyIntegrationTestConfigDelegate;
    }

    static WildFlyOpenshiftIntegrationTestConfig adapt(WildFlyOpenshiftIntegrationTest annotation) {
        return new WildFlyOpenshiftIntegrationTestConfig(
                annotation.deployEnabled(),
                annotation.buildEnabled(),
                annotation.pushEnabled(),
                annotation.imageStreamTagTimeout(),
                annotation.readinessTimeout(),
                annotation.additionalModules(),
                WildFlyIntegrationTestConfigDelegate.create(annotation));
    }

    @Override
    public String getNamespace() {
        return wildFlyIntegrationTestConfigDelegate.getNamespace();
    }

    public List<KubernetesResource> getKubernetesResources() {
        return wildFlyIntegrationTestConfigDelegate.getKubernetesResources();
    }

    public ExtraTestSetup getExtraTestSetup() {
        return wildFlyIntegrationTestConfigDelegate.getExtraTestSetup();
    }

    public Map<String, ConfigPlaceholderReplacer> getPlaceholderReplacements() {
        return wildFlyIntegrationTestConfigDelegate.getPlaceholderReplacements();
    }

    public void addAdditionalKubernetesResources(List<KubernetesResource> additionalKubernetesResources) {
        wildFlyIntegrationTestConfigDelegate.addAdditionalKubernetesResources(additionalKubernetesResources);
    }
}
