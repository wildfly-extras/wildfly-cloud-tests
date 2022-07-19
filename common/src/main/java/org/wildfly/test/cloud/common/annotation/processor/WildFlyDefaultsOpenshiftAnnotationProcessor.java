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

package org.wildfly.test.cloud.common.annotation.processor;

import java.util.Map;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;

import io.dekorate.doc.Description;
import io.dekorate.kubernetes.annotation.Port;
import io.dekorate.openshift.annotation.OpenshiftApplication;
import io.dekorate.s2i.annotation.S2iBuild;

/**
 * Adds the following to the config:
 * <ul>
 *     <li>ports 8080 and 9990</li>
 *     <li>the env var SERVER_PUBLIC_BIND_ADDRESS=0.0.0.0</li>
 *     <li>Generates the Dockerfile to create the image, and adds the CLI script to trigger it if it exists</li>
 * </ul>
 */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@Description("Generates kubernetes manifests.")
@SupportedAnnotationTypes("io.dekorate.openshift.annotation.OpenshiftApplication")
public class WildFlyDefaultsOpenshiftAnnotationProcessor extends WildFlyDefaultsAbstractAnnotationProcessor {

    @Override
    Class<OpenshiftApplication> getAnnotationClass() {
        return OpenshiftApplication.class;
    }

    @Override
    Port[] getPorts(Element mainClass) {
        return mainClass.getAnnotation(getAnnotationClass()).ports();
    }

    @Override
    String getEnvVarPrefix() {
        return "dekorate.openshift.";
    }

    @Override
    String getPortPrefix() {
        return "dekorate.openshift.";
    }

    @Override
    void addAddtionalProperties(Map<String, Object> inputProperties, S2iBuild s2iBuild) {
        if (s2iBuild == null) {
            // Let dekorate handle this since the test is set up differently from expected
            inputProperties.put("dekorate.s2i.enabled", "false");
        }
        // TODO - figure out how to make these two dynamic
        inputProperties.put("dekorate.docker.registry", "default-route-openshift-image-registry.apps.sandbox.x8i5.p1.openshiftapps.com");
        inputProperties.put("dekorate.docker.group", "kkhan1-dev");
    }
}
