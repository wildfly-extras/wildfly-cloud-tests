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

package org.wildfly.cloud.tests.extension;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jboss.shrinkwrap.api.Archive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.wildfly.cloud.tests.api.Deployment;
import org.wildfly.cloud.tests.util.ArchiveToDockerImageUtil;

/**
 * Looks for the @{@link org.wildfly.cloud.tests.api.Deployment} annotation onODocker
 * a class's <i>static</i> methods and creates a Docker image with the resulting
 * information.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class CreateDeploymentImageExtension implements Extension, BeforeAllCallback, AfterAllCallback {
    private volatile String createdArchiveName;

    private CreateDeploymentImageExtension() {
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        Method method = getDeploymentAnnotatedMethod(extensionContext);
        Deployment deployment = method.getAnnotation(Deployment.class);

        Archive<?> archive = (Archive<?>) method.invoke(null);
        Assertions.assertNotNull(archive, "Null archive returned from " + method);
        String fromImageName = deployment.fromImage().trim();
        Assertions.assertNotEquals("", fromImageName, "Empty @Deployment.fromImage");
        createdArchiveName = deployment.toImage().trim();
        Assertions.assertNotEquals("", createdArchiveName, "Empty @Deployment.toImage");

        ArchiveToDockerImageUtil util = new ArchiveToDockerImageUtil(deployment.fromImage(), createdArchiveName, archive);
        util.createImageWithArchiveDeployment();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
    }


    private Method getDeploymentAnnotatedMethod(ExtensionContext extensionContext) {
        Class<?> clazz = extensionContext.getRequiredTestClass();
        Method foundMethod = null;
        for (Method method : clazz.getMethods()) {
            Deployment deployment = method.getAnnotation(Deployment.class);
            if (deployment != null) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException("Method " + method + " is annotated with @Deployment but is not static. " +
                            "Only static methods are supported with this annotation");
                }
                if (foundMethod != null) {
                    throw new IllegalStateException("Only one method can be annotated with @Deployment. Found both " + method + " and " + foundMethod);
                }
                foundMethod = method;
            }
        }
        if (foundMethod == null) {
            throw new IllegalStateException("Found no method annotated with @Deployment");
        }
        return foundMethod;
    }
}
