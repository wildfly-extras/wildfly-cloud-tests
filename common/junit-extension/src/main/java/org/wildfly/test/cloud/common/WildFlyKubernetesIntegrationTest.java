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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Extends io.dekorate.testing.annotation.KubernetesIntegrationTest
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@Target({ TYPE, METHOD, ANNOTATION_TYPE })
@Retention(RUNTIME)
@ExtendWith(WildFlyKubernetesExtension.class)
public @interface WildFlyKubernetesIntegrationTest {

    ////////////////////////////////////////////////////////////////////////
    // Fields from io.dekorate.testing.annotation.KubernetesIntegrationTest
    ////////////////////////////////////////////////////////////////////////

    /**
     * Flag to define whether the extension should automatically apply resources.
     *
     * @return True, if extension should automatically deploy dekorate generated resources.
     * @see io.dekorate.testing.annotation.KubernetesIntegrationTest
     */
    boolean deployEnabled() default true;

    /**
     * Flag to define whether the extension should automatically apply resources.
     *
     * @return True, if extensions should automatically perform container builds.
     * @see io.dekorate.testing.annotation.KubernetesIntegrationTest
     */
    boolean buildEnabled() default true;

    /**
     * The amount of time in milliseconds to wait for application to become ready.
     *
     * @return The max amount in milliseconds.
     * @see io.dekorate.testing.annotation.KubernetesIntegrationTest
     */
    long readinessTimeout() default 500000;

    /**
     * List of additional modules to be loaded by the test framework.
     *
     * @return The list of additional modules to be loaded.
     * @see io.dekorate.testing.annotation.KubernetesIntegrationTest
     */
    String[] additionalModules() default {};

    ////////////////////////////////////////////////////////////////////////
    // WildFly specific fields
    ////////////////////////////////////////////////////////////////////////

    /**
     * The namespace to deploy the test application and other resources into.
     * If empty, the default namespace will be used
     *
     * @return the namespace
     */
    String namespace() default "";

    /**
     * Any resources to be deployed before the application is deployed. This could
     * be things like CRDs or pods based on those CRDs.
     *
     * @return the list of resources
     */
    KubernetesResource[] kubernetesResources() default {};

    /**
     * A class to do more advanced test initialisation.
     *
     * @return the class
     */
    Class<? extends ExtraTestSetup> extraTestSetup() default ExtraTestSetup.None.class;

    /**
     * A list of replacements to do in the generated kubernetes.yml and in the {@code #kubernetesResources}.
     * Useful for when we don't have the full information needed until the test is run. The replacements are done
     * before deploying to Kubernetes.
     *
     * @return the list of replacements
     */
    ConfigPlaceholderReplacement[] placeholderReplacements() default {};

    /**
     * A list of value injectors used to {@code @Inject} global fields in tests.
     *
     * @return an array of value injectors
     */
    Class<? extends ValueInjector>[] valueInjectors() default {};
}
