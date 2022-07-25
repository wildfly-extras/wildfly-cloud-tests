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
import org.wildfly.test.cloud.common.ConfigPlaceholderReplacer;

public class Replacers {
    public static class NameReplacer implements ConfigPlaceholderReplacer {
        @Override
        public String replace(ExtensionContext context, String placeholder, String line) {
            return Replacers.replace(placeholder, line);
        }
    }

    public static class ValueReplacer implements ConfigPlaceholderReplacer {
        @Override
        public String replace(ExtensionContext context, String placeholder, String line) {
            return Replacers.replace(placeholder, line);
        }
    }

    private static String replace(String placeholder, String line) {
        return line.replace(placeholder, getReplacement(placeholder));
    }

    private static String getReplacement(String placeholder) {
        switch (placeholder) {
            case "$NAME-B$":
                return "additional-b";
            case "$VALUE-B$":
                return "B";
            case "$NAME-D$":
                return "resource-d";
            case "$VALUE-D$":
                return "D";
        }
        throw new IllegalArgumentException("Unknown placeholder: " + placeholder);
    }
}
