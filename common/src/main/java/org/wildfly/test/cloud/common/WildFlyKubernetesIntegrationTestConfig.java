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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.dekorate.testing.config.EditableKubernetesIntegrationTestConfig;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class WildFlyKubernetesIntegrationTestConfig extends EditableKubernetesIntegrationTestConfig {

    private final String namespace;
    private final List<KubernetesResource> kubernetesResources;
    private final ExtraTestSetup extraTestSetup;


    private WildFlyKubernetesIntegrationTestConfig(boolean deployEnabled, boolean buildEnabled, long readinessTimeout,
                                                   String[] additionalModules, String namespace,
                                                   KubernetesResource[] kubernetesResources,
                                                   Class<? extends ExtraTestSetup> additionalTestSetupClass){
        super(deployEnabled, buildEnabled, readinessTimeout, additionalModules);
        this.namespace = namespace;
        this.kubernetesResources = new ArrayList<>(Arrays.asList(kubernetesResources));
        try {
            extraTestSetup = (additionalTestSetupClass == null) ?
                    null : additionalTestSetupClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    static WildFlyKubernetesIntegrationTestConfig adapt(WildFlyKubernetesIntegrationTest annotation) {
        return new WildFlyKubernetesIntegrationTestConfig(
                annotation.deployEnabled(),
                annotation.buildEnabled(),
                annotation.readinessTimeout(),
                annotation.additionalModules(),
                annotation.namespace(),
                annotation.kubernetesResources(),
                annotation.extraTestSetup());
    }

    public String getNamespace() {
        return namespace;
    }

    public List<KubernetesResource> getKubernetesResources() {
        return kubernetesResources;
    }

    ExtraTestSetup getExtraTestSetup() {
        return extraTestSetup;
    }

    void addAdditionalKubernetesResources(List<KubernetesResource> additionalKubernetesResources) {
        //Since the additional resources are likely needed by the actual deployment,
        //add them first
        List<KubernetesResource> temp = new ArrayList<>(additionalKubernetesResources);
        temp.addAll(kubernetesResources);

        kubernetesResources.clear();
        kubernetesResources.addAll(temp);
    }
}
