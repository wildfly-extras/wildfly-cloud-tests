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

import static io.dekorate.kubernetes.annotation.ImagePullPolicy.Always;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import io.dekorate.kubernetes.annotation.ConfigMapVolume;
import io.dekorate.kubernetes.annotation.Env;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Mount;
import io.dekorate.kubernetes.annotation.SecretVolume;
import io.dekorate.option.annotation.GeneratorOptions;

@KubernetesApplication(
        envVars = {
                @Env(name = "CONFIG_ENV_VAR", value = "From env var")
        },
        configMapVolumes = {@ConfigMapVolume(configMapName = "my-config-map", volumeName = "my-config-map", defaultMode = 0666)},
        secretVolumes = {@SecretVolume(secretName = "my-secret", volumeName = "my-secret", defaultMode = 0666)},
        mounts = {
                @Mount(name = "my-config-map", path = "/etc/config/my-config-map"),
                @Mount(name = "my-secret", path = "/etc/config/my-secret")},
        imagePullPolicy = Always)
@ApplicationPath("")
@GeneratorOptions(inputPath = "kubernetes")
public class MpConfigApp extends Application {

}
