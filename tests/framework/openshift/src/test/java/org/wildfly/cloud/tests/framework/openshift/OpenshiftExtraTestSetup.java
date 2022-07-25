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

package org.wildfly.cloud.tests.framework.openshift;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.wildfly.test.cloud.common.ExtraTestSetup;
import org.wildfly.test.cloud.common.KubernetesResource;

import java.util.Collections;
import java.util.List;

public class OpenshiftExtraTestSetup implements ExtraTestSetup {
    static boolean called = false;
    @Override
    public List<KubernetesResource> beforeAll(ExtensionContext context) {
        called = true;
        return Collections.emptyList();
    }
}
