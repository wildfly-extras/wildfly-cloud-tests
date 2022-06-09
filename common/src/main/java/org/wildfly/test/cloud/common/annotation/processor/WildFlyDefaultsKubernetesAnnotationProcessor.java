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

package org.wildfly.test.cloud.common.annotation.processor;

import io.dekorate.Logger;
import io.dekorate.LoggerFactory;
import io.dekorate.doc.Description;
import io.dekorate.processor.AbstractAnnotationProcessor;
import io.dekorate.utils.Maps;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds the following to the config:
 * <ul>
 *     <li>ports 8080 and 9990</li>
 *     <li>the env var SERVER_PUBLIC_BIND_ADDRESS=0.0.0.0</li>*
 * </ul>
 */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@Description("Generates kubernetes manifests.")
@SupportedAnnotationTypes("io.dekorate.kubernetes.annotation.KubernetesApplication")
public class WildFlyDefaultsKubernetesAnnotationProcessor extends AbstractAnnotationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger();

    private static final String DOCKER_FILE_RELATIVE_TO_TARGET = "docker/Dockerfile";
    private static final String GENERATED_DOCKER_FILE_LOCATION = "target/" + DOCKER_FILE_RELATIVE_TO_TARGET;

    private static final String CLI_SCRIPT_SOURCE = "src/main/cli-script/init.cli";
    private static final String CLI_SCRIPT_IN_IMAGE = "cli-script/init.cli";
    private static final String CLI_LAUNCH_SCRIPT_VAR = "CLI_LAUNCH_SCRIPT";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            getSession().close();
            return true;
        }

        // We don't actually care about the annotation properties here, that is handled by dekorate.
        // Instead we will add our defaults with properties if the annotation is found.
        for (TypeElement typeElement : annotations) {
            boolean found = false;
            for (Element mainClass : roundEnv.getElementsAnnotatedWith(typeElement)) {
                LOGGER.info("Found @KubernetesApplication on: " + mainClass.toString());
                if (found) {
                    throw new IllegalStateException("More than one @KubernetesApplication class found on classpath");
                }

                Path targetDirectory = determineTargetDirectory(mainClass);
                Path cliScript = targetDirectory.getParent().resolve(CLI_SCRIPT_SOURCE).normalize().toAbsolutePath();

                if (Files.exists(cliScript)) {
                    LOGGER.info("Found %s. Will add it to the image.", cliScript);
                } else {
                    LOGGER.info("No CLI script found at %s. Not adding to image", cliScript);
                    cliScript = null;
                }

                generateDockerFile(targetDirectory, cliScript);



                addDefaults(cliScript != null);

                mainClass.getSimpleName();


                found = true;
            }
        }

        return false;
    }

    private void addDefaults(boolean cliScriptAdded) {
        Map<String, Object> inputProperties = new HashMap<>();

        inputProperties.put("dekorate.kubernetes.env-vars[0].name", "SERVER_PUBLIC_BIND_ADDRESS");
        inputProperties.put("dekorate.kubernetes.env-vars[0].value", "0.0.0.0");

        if (cliScriptAdded) {
            inputProperties.put("dekorate.kubernetes.env-vars[1].name", CLI_LAUNCH_SCRIPT_VAR);
            inputProperties.put("dekorate.kubernetes.env-vars[1].value", CLI_SCRIPT_IN_IMAGE);
        }

        inputProperties.put("dekorate.kubernetes.ports[0].name", "http");
        inputProperties.put("dekorate.kubernetes.ports[0].containerPort", "8080");

        inputProperties.put("dekorate.kubernetes.ports[1].name", "admin");
        inputProperties.put("dekorate.kubernetes.ports[1].containerPort", "9990");

        //Not needed at the moment.
        // It seemed needed when using -A to pass in values when trying to debug. Now we use system properies instead.
        // See the compiler plugin in ../tests/pom.xml for more details
        /*
        String registry = processingEnv.getOptions().get("dekorate.docker.registry");
        if (registry != null) {
            inputProperties.put("dekorate.docker.registry", registry);
        }
        String push = processingEnv.getOptions().get("dekorate.push");
        if (registry != null) {
            inputProperties.put("dekorate.docker.push", push);
        }
        */

        // We want to generate our DockerFile, so override the location
        inputProperties.put("dekorate.docker.docker-file", GENERATED_DOCKER_FILE_LOCATION);

        Map<String, Object> properties = Maps.fromProperties(inputProperties);

        getSession().addPropertyConfiguration(properties);

    }

    private Path determineTargetDirectory(Element mainClass) {
        String qualifiedName = ((TypeElement) mainClass).getQualifiedName().toString();
        String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
        Path path;
        try {
            FileObject fileObject = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "tmp", mainClass.getSimpleName() + ".class");
            path = Paths.get(fileObject.toUri());

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        while (path != null && !path.getFileName().toString().equals("target")) {
            path = path.getParent();
        }
        return path;
    }

    private void generateDockerFile(Path targetDirectory, Path cliScript) {
        String imageName = processingEnv.getOptions().get("wildfly.cloud.test.base.image.name");
        if (imageName == null || imageName.trim().isBlank()) {
            throw new IllegalStateException("No image name set via the 'wildfly.cloud.test.base.image.name' property in the test module pom");
        }

        List<String> lines = new ArrayList<>();
        lines.add("FROM " + imageName);
        lines.add("COPY --chown=jboss:root target/ROOT.war $JBOSS_HOME/standalone/deployments");



        if (cliScript != null) {
            // We will copy the CLI script to the directory of the Dockerfile further down
            //lines.add("COPY --chown=jboss:root " + cliScript.getFileName().toString() + " $JBOSS_HOME/" + CLI_SCRIPT_IN_IMAGE);
            lines.add("COPY --chown=jboss:root " + CLI_SCRIPT_SOURCE + " $JBOSS_HOME/" + CLI_SCRIPT_IN_IMAGE);
        }

        try {

            Path dockerFile = targetDirectory.resolve(DOCKER_FILE_RELATIVE_TO_TARGET);
            if (Files.exists(dockerFile)) {
                Files.delete(dockerFile);
            }
            if (!Files.exists(dockerFile.getParent())) {
                Files.createDirectories(dockerFile.getParent());
            }
            Files.createFile(dockerFile);
            Files.write(dockerFile, lines);

            if (cliScript != null) {
                Files.copy(cliScript, dockerFile.getParent().resolve(cliScript.getFileName()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
