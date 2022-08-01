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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class WildFlyIntegrationTestConfigDelegate {
    private final String namespace;
    private final List<KubernetesResource> kubernetesResources;
    private final Map<String, ConfigPlaceholderReplacer> placeholderReplacements;
    private final ExtraTestSetup extraTestSetup;

    private WildFlyIntegrationTestConfigDelegate(
            String namespace,
            KubernetesResource[] kubernetesResources,
            ConfigPlaceholderReplacement[] placeholderReplacements,
            Class<? extends ExtraTestSetup> additionalTestSetupClass) {
        this.namespace = namespace;
        this.kubernetesResources = new ArrayList<>(Arrays.asList(kubernetesResources));
        Map<String, ConfigPlaceholderReplacer> replacements = new LinkedHashMap<>();

        try {
            extraTestSetup = (additionalTestSetupClass == null) ?
                    null : additionalTestSetupClass.getDeclaredConstructor().newInstance();
            for (ConfigPlaceholderReplacement replacement : placeholderReplacements) {
                replacements.put(replacement.placeholder(), replacement.replacer().getDeclaredConstructor().newInstance());
            }
        } catch (Exception e) {
            throw new RuntimeException();
        }
        this.placeholderReplacements = replacements;
    }

    static WildFlyIntegrationTestConfigDelegate create(WildFlyKubernetesIntegrationTest annotation) {
        return new WildFlyIntegrationTestConfigDelegate(
                annotation.namespace(), annotation.kubernetesResources(), annotation.placeholderReplacements(), annotation.extraTestSetup());
    }

    static WildFlyIntegrationTestConfigDelegate create(WildFlyOpenshiftIntegrationTest annotation) {
        return new WildFlyIntegrationTestConfigDelegate(
                "", annotation.kubernetesResources(), annotation.placeholderReplacements(), annotation.extraTestSetup());
    }

    public String getNamespace() {
        return namespace;
    }

    public List<KubernetesResource> getKubernetesResources() {
        return kubernetesResources;
    }

    public Map<String, ConfigPlaceholderReplacer> getPlaceholderReplacements() {
        return placeholderReplacements;
    }

    public ExtraTestSetup getExtraTestSetup() {
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
