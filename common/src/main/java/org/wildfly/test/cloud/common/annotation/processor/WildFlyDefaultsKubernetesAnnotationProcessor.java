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

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;

import io.dekorate.doc.Description;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Port;

/**
 * Adds the following to the config:
 * <ul>
 *     <li>ports 8080 and 9990</li>
 *     <li>Generates the Dockerfile to create the image, and adds the CLI script to trigger it if it exists</li>
 * </ul>
 */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@Description("Generates kubernetes manifests.")
@SupportedAnnotationTypes("io.dekorate.kubernetes.annotation.KubernetesApplication")
public class WildFlyDefaultsKubernetesAnnotationProcessor extends WildFlyDefaultsAbstractAnnotationProcessor {

    @Override
    Class<KubernetesApplication> getAnnotationClass() {
        return KubernetesApplication.class;
    }

    @Override
    Port[] getPorts(Element mainClass) {
        return mainClass.getAnnotation(getAnnotationClass()).ports();
    }

    @Override
    String getEnvVarPrefix() {
        return "dekorate.kubernetes.";
    }

    @Override
    String getPortPrefix() {
        return "dekorate.kubernetes.";
    }
}
