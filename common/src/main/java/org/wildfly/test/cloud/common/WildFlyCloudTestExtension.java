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

import static java.util.Arrays.stream;

import java.util.Arrays;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.dekorate.DekorateException;
import io.dekorate.testing.WithProject;
import io.dekorate.testing.kubernetes.KubernetesExtension;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WildFlyCloudTestExtension implements Extension, BeforeEachCallback, WithProject {
    private final KubernetesExtension delegate = new KubernetesExtension();
    public WildFlyCloudTestExtension() {
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        String projectName = delegate.getName(context);
        TestHelper helper = new TestHelper(
                delegate.getKubernetesClient(context),
                projectName);

        Object testInstance = context.getTestInstance().get();
        Class clazz = testInstance.getClass();
        while (clazz != Object.class) {
            Arrays.stream(clazz.getDeclaredFields())
                    .forEach(field -> {
                        if (!field.getType().isAssignableFrom(TestHelper.class)) {
                            return;
                        }

                        //This is to make sure we don't write on fields by accident.
                        //Note: we don't require the exact annotation. Any annotation named Inject will do (be it javax, guice etc)
                        if (!stream(field.getDeclaredAnnotations()).filter(a -> a.annotationType().getSimpleName().equalsIgnoreCase("Inject"))
                                .findAny().isPresent()) {
                            return;
                        }

                        field.setAccessible(true);
                        try {
                            field.set(testInstance, helper);
                        } catch (IllegalAccessException e) {
                            throw DekorateException.launderThrowable(e);
                        }

                    });
            clazz = clazz.getSuperclass();
        }

    }

    @Override
    public String[] getAdditionalModules(ExtensionContext context) {
        return new String[0];
    }
}
