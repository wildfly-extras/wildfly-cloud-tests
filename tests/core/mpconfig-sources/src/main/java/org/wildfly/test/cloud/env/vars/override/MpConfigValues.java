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

package org.wildfly.test.cloud.env.vars.override;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MpConfigValues {

    private String configEnvVar;
    private String deploymentProperty;
    private String configMapProperty;
    private String secretProperty;

    public String getConfigEnvVar() {
        return configEnvVar;
    }

    public void setConfigEnvVar(String configEnvVar) {
        this.configEnvVar = configEnvVar;
    }

    public String getDeploymentProperty() {
        return deploymentProperty;
    }

    public void setDeploymentProperty(String deploymentProperty) {
        this.deploymentProperty = deploymentProperty;
    }

    public String getConfigMapProperty() {
        return configMapProperty;
    }

    public void setConfigMapProperty(String configMapProperty) {
        this.configMapProperty = configMapProperty;
    }

    public String getSecretProperty() {
        return secretProperty;
    }

    public void setSecretProperty(String secretProperty) {
        this.secretProperty = secretProperty;
    }
}
