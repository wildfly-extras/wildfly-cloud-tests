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
package org.wildfly.cloud.tests.framework.kubernetes;

import io.dekorate.kubernetes.annotation.ConfigMapVolume;
import io.dekorate.kubernetes.annotation.Env;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Mount;
import io.dekorate.option.annotation.GeneratorOptions;

import static io.dekorate.kubernetes.annotation.ImagePullPolicy.Always;

@KubernetesApplication(
        envVars = {
                @Env(name = "CONFIG_ENV_VAR", value = "From env var")
        },
        configMapVolumes = {
                @ConfigMapVolume(configMapName = "additional-a", volumeName = "additional-a", defaultMode = 0666),
                @ConfigMapVolume(configMapName = "additional-b", volumeName = "additional-b", defaultMode = 0666),
                @ConfigMapVolume(configMapName = "resource-c", volumeName = "resource-c", defaultMode = 0666),
                @ConfigMapVolume(configMapName = "resource-d", volumeName = "resource-d", defaultMode = 0666)
        },
        mounts = {
                @Mount(name = "additional-a", path = "/etc/config/a"),
                @Mount(name = "additional-b", path = "/etc/config/b"),
                @Mount(name = "resource-c", path = "/etc/config/c"),
                @Mount(name = "resource-d", path = "/etc/config/d")
        },
        imagePullPolicy = Always)
@GeneratorOptions(inputPath = "additional")
public class KubernetesFrameworkTestMain {

}
