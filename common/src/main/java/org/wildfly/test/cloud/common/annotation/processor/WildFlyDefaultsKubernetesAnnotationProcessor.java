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

import io.dekorate.Logger;
import io.dekorate.LoggerFactory;
import io.dekorate.doc.Description;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.processor.AbstractAnnotationProcessor;
import io.dekorate.utils.Maps;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Adds the following to the config:
 * <ul>
 *     <li>ports 8080 and 9990</li>
 *     <li>the env var SERVER_PUBLIC_BIND_ADDRESS=0.0.0.0</li>*
 * </ul>
 */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@Description("Generates kubernetes manifests.")
@SupportedAnnotationTypes("io.dekorate.kubernetes.annotation.KubernetesApplication")
public class WildFlyDefaultsKubernetesAnnotationProcessor extends AbstractAnnotationProcessor {

    private final Logger LOGGER = LoggerFactory.getLogger();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            getSession().close();
            return true;
        }

        // We don't actually care about the annotation properties here, that is handled by dekorate.
        // Instead we will add our defaults with properties if the annotation is found.
        for (TypeElement typeElement : annotations) {
            for (Element mainClass : roundEnv.getElementsAnnotatedWith(typeElement)) {
                LOGGER.info("Found @KubernetesApplication on: " + mainClass.toString());
                //if (true) throw new IllegalStateException("Blah");
                // Add the  properties
                addDefaults();
            }
        }

        return false;
    }

    private void addDefaults() {
        Map<String, Object> inputProperties = new HashMap<>();

        inputProperties.put("dekorate.kubernetes.env-vars[0].name", "SERVER_PUBLIC_BIND_ADDRESS");
        inputProperties.put("dekorate.kubernetes.env-vars[0].value", "0.0.0.0");

        inputProperties.put("dekorate.kubernetes.ports[0].name", "http");
        inputProperties.put("dekorate.kubernetes.ports[0].containerPort", "8080");

        inputProperties.put("dekorate.kubernetes.ports[0].name", "admin");
        inputProperties.put("dekorate.kubernetes.ports[0].containerPort", "9990");

        Map<String, Object> properties = Maps.fromProperties(inputProperties);

        getSession().addPropertyConfiguration(properties);
    }

}
