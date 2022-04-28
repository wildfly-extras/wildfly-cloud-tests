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

package org.wildfly.test.cloud.microprofile.datasources.postgresql;

import io.dekorate.kubernetes.annotation.Env;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Port;
import io.dekorate.option.annotation.GeneratorOptions;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import static io.dekorate.kubernetes.annotation.ImagePullPolicy.Always;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@KubernetesApplication(
        ports = {
                @Port(name = "web", containerPort = 8080),
                @Port(name = "admin", containerPort = 9990)
        },
        envVars = {
                @Env(name = "SERVER_PUBLIC_BIND_ADDRESS", value = "0.0.0.0"),
                // Env vars for the datasource provisioned by the image
                @Env(name = "POSTGRESQL_DATABASE", value= "postgresdb"),
                @Env(name = "POSTGRESQL_USER", value = "postgresadmin"),
                @Env(name = "POSTGRESQL_PASSWORD", value = "admin12"),
                @Env(name = "POSTGRESQL_HOST", value = "postgres-service"),
                @Env(name = "POSTGRESQL_PORT", value = "5432"),
                // Env vars for the launch scripts to create a datasource
                // gives JNDI name java:jboss/datasources/test_postgresql
                @Env(name = "DB_SERVICE_PREFIX_MAPPING", value = "test-postgresql=LAUNCH"),
                @Env(name = "LAUNCH_DATABASE", value= "postgresdb"),
                @Env(name = "LAUNCH_USERNAME", value = "postgresadmin"),
                @Env(name = "LAUNCH_PASSWORD", value = "admin12"),
                @Env(name = "TEST_POSTGRESQL_SERVICE_HOST", value = "postgres-service"),
                @Env(name = "TEST_POSTGRESQL_SERVICE_PORT", value = "5432")
        },
        imagePullPolicy = Always)
@ApplicationPath("")
@GeneratorOptions(inputPath = "kubernetes")
public class PostgresDatasourceApp extends Application {
}
