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

import io.dekorate.testing.WithKubernetesClient;
import io.fabric8.kubernetes.api.model.Secret;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.wildfly.test.cloud.common.ExtraTestSetup;
import org.wildfly.test.cloud.common.KubernetesResource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An ExtraTestSetup implementation for running on CI. It converts
 * `rhoas generate-config` values stored in GitHub secrets and passed in to
 * the test as system properties into a Kubernetes secret, which is then deployed.
 */
public class RhosakCliAdditionalTestSetup implements ExtraTestSetup, WithKubernetesClient {
    @Override
    public List<KubernetesResource> beforeAll(ExtensionContext context) {
        System.out.println();
        String kafkaHost = System.getProperty("wildfly.cloud.test.manual.rhoas.kafka.host");
        String clientId = System.getProperty("wildfly.cloud.test.manual.rhoas.client.id");
        String clientSecret = System.getProperty("wildfly.cloud.test.manual.rhoas.client.secret");

        if (kafkaHost == null || clientId == null || clientSecret == null) {
            System.out.println("One or more of wildfly.cloud.test.manual.rhoas.kafka.host, wildfly.cloud.test.manual.rhoas.client.id and wildfly.cloud.test.manual.rhoas.client.secret were not set up. Assuming that the 'rhoas' secret was set up manually");

            // Check the secret was set up manually
            List<Secret> secrets = getKubernetesClient(context).secrets().list().getItems().stream().filter(c -> c.getMetadata().getName().equals("rhoas")).collect(Collectors.toList());
            if (secrets.size() == 0) {
                throw new IllegalStateException("'rhoas' secret does not exist");
            }
            return Collections.emptyList();
        }
        System.out.println("wildfly.cloud.test.manual.rhoas.kafka.host, wildfly.cloud.test.manual.rhoas.client.id and wildfly.cloud.test.manual.rhoas.client.secret are configured. Generating Kafka secret.");

        URL url = this.getClass().getResource("/rhoas-secret-template.yml");
        Assertions.assertNotNull(url);


        Path secretDefinitionPath;
        try {
            List<String> lines = Files.readAllLines(Paths.get(url.toURI()));
            List<String> newLines = new ArrayList<>();
            for (String line : lines) {
                line = line.replace("${wildfly.cloud.test.manual.rhoas.kafka.host}", base64(kafkaHost));
                line = line.replace("${wildfly.cloud.test.manual.rhoas.client.id}", base64(clientId));
                line = line.replace("${wildfly.cloud.test.manual.rhoas.client.secret}", base64(clientSecret));
                newLines.add(line);
            }

            secretDefinitionPath = Path.of("target", "kubernetes-input");
            Files.createDirectories(secretDefinitionPath);
            secretDefinitionPath = Files.createTempFile(secretDefinitionPath, "kb-secret", ".yml");
            Files.write(secretDefinitionPath, newLines);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return Collections.singletonList(new KubernetesResource.Literal().withDefinitionLocation(secretDefinitionPath.toString()));
    }

    private String base64(String s) {
        byte[] encoded = Base64.getEncoder().encode(s.getBytes(StandardCharsets.UTF_8));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
