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

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.extension.ExtensionContext;

public interface ExtraTestSetup {
    /**
     * Called during the beforeAll stage of each test. May or may not return any KubernetesResources.
     * If it returns KubernetesResources, they will be added to the list in the test's
     * WildFlyKubernetesIntegrationTest.kubernetesResources.
     *
     * @param context
     * @return the added resources. Must not be {@code null}.
     */
    default List<KubernetesResource> beforeAll(ExtensionContext context) {
        return Collections.emptyList();
    }

    class None implements ExtraTestSetup {

    }
}
