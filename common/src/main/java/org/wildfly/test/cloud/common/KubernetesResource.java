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

import java.lang.annotation.Annotation;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public @interface KubernetesResource {
    /**
     * The location of the the resource definition. This can either be a local file or a URL.
     * If it is a local file, and not absolute, the location is taken to be relative to the
     * root of the project containing the test.
     *
     * @return the location of the resource definition
     */
    String definitionLocation();

    /**
     * Additional resources to wait for to be ready. In some cases they are not listed
     * by the resources in {@link #definitionLocation()} but rather created 'behind
     * the scenes.
     *
     * @return The additional Resources to wait for
     */
    Resource[] additionalResourcesCreated() default {};


    /**
     * How long to wait for the resource to become active
     *
     * @return the timeout
     */
    long readinessTimeout() default 500000;

    public class Literal implements KubernetesResource {
        private Resource[] additionalResourcesCreated = new Resource[0];
        private long readinessTimeout = 50000;
        private String definitionLocation;

        @Override
        public Class<? extends Annotation> annotationType() {
            return KubernetesResource.class;
        }

        @Override
        public String definitionLocation() {
            return definitionLocation;
        }

        @Override
        public Resource[] additionalResourcesCreated() {
            return new Resource[0];
        }

        @Override
        public long readinessTimeout() {
            return readinessTimeout;
        }

        public Literal withDefinitionLocation(String definitionLocation) {
            this.definitionLocation = definitionLocation;
            return this;
        }

        public Literal withReadinessTimeout(long readinessTimeout) {
            this.readinessTimeout = readinessTimeout;
            return this;
        }

        public Literal withAdditionalResourcesCreated(Resource... additionalResourcesCreated) {
            Resource[] tmp = new Resource[this.additionalResourcesCreated.length + additionalResourcesCreated.length];
            System.arraycopy(this.additionalResourcesCreated, 0, tmp, 0, this.additionalResourcesCreated.length);
            System.arraycopy(additionalResourcesCreated, 0, tmp, this.additionalResourcesCreated.length, additionalResourcesCreated.length);
            this.additionalResourcesCreated = additionalResourcesCreated;
            return this;
        }
    }
}
