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

package org.wildfly.test.cloud.microprofile.reactive.messaging.strimzi;

import static io.dekorate.kubernetes.annotation.ImagePullPolicy.Always;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import io.dekorate.kubernetes.annotation.Env;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Port;

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
                @Env(name = "MP_MESSAGING_CONNECTOR_SMALLRYE_KAFKA_BOOTSTRAP_SERVERS", value= "my-cluster-kafka-bootstrap:9092")
        },
        imagePullPolicy = Always)
@ApplicationPath("")
public class ReactiveMessagingWithStrimziApp extends Application {
}
