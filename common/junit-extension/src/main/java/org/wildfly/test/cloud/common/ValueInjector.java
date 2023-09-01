/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.test.cloud.common;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Used to inject field values in tests that are annotated with {@code @Inject}.
 *
 * <p>
 * Note that implementations must have a public no-arg constructor.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ValueInjector {

    /**
     * Determines if this injector can handle the fields type.
     *
     * @param type the fields type
     *
     * @return {@code true} if this injector can handle the field type, otherwise {@code false}
     */
    default boolean canInject(Class<?> type) {
        return type.isAssignableFrom(supportedType());
    }

    /**
     * Returns the type this injector can inject.
     *
     * @return the type this injector can inject
     */
    Class<?> supportedType();

    /**
     * Returns the value to be injected.
     *
     * @param context the extensions context
     *
     * @return the value to be injected
     */
    Object resolve(ExtensionContext context);
}
