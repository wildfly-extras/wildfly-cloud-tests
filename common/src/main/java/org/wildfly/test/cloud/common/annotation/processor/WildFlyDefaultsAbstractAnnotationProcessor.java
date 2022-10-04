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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.dekorate.Logger;
import io.dekorate.LoggerFactory;
import io.dekorate.kubernetes.annotation.Port;
import io.dekorate.processor.AbstractAnnotationProcessor;
import io.dekorate.project.AptProjectFactory;
import io.dekorate.s2i.annotation.S2iBuild;
import io.dekorate.utils.Maps;

/**
 * Adds the following to the config:
 * <ul>
 *     <li>ports 8080 and 9990</li>
 *     <li>Generates the Dockerfile to create the image, and adds the CLI script to trigger it if it exists</li>
 * </ul>
 */
abstract class WildFlyDefaultsAbstractAnnotationProcessor extends AbstractAnnotationProcessor {

    protected static final Logger LOGGER = LoggerFactory.getLogger();

    private static final String DOCKER_FILE = "Dockerfile";
    private static final String DOCKER_FILE_RELATIVE_TO_TARGET = "docker/" + DOCKER_FILE;
    private static final String GENERATED_DOCKER_FILE_LOCATION = "target/" + DOCKER_FILE_RELATIVE_TO_TARGET;

    private static final String CLI_SCRIPT_SOURCE = "src/main/cli-script/init.cli";
    private static final String CLI_SCRIPT_IN_IMAGE = "cli-script/init.cli";
    private static final String CLI_LAUNCH_SCRIPT_VAR = "CLI_LAUNCH_SCRIPT";

    private final AtomicReference<ProcessingEnvironment> processingEnvRef = new AtomicReference<>();

    WildFlyDefaultsAbstractAnnotationProcessor() {

    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.processingEnvRef.set(processingEnv);

        if (!projectExists()) {
            setProject(AptProjectFactory.create(processingEnv));
        }
    }

    abstract Class<? extends Annotation> getAnnotationClass();

    abstract Port[] getPorts(Element mainClass);

    abstract String getEnvVarPrefix();

    abstract String getPortPrefix();

    void addAddtionalProperties(Map<String, Object> inputProperties, S2iBuild s2iBuild) {
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingEnv = processingEnvRef.get();
        LOGGER.info(this.getClass().getSimpleName() + " looking for annotations: " + annotations);
        if (roundEnv.processingOver()) {
            getSession().close();
            return true;
        }

        // We don't actually care about the annotation properties here, that is handled by dekorate.
        // Instead we will add our defaults with properties if the annotation is found.
        for (TypeElement typeElement : annotations) {
            boolean found = false;
            for (Element mainClass : roundEnv.getElementsAnnotatedWith(typeElement)) {
                LOGGER.info(this.getClass().getSimpleName() + " found @" + getAnnotationClass().getSimpleName() + " on: " + mainClass.toString());
                if (found) {
                    throw new IllegalStateException("More than one @" + getAnnotationClass().getSimpleName() + " class found on classpath");
                }
                found = true;

                Port[] ports = getPorts(mainClass);
                Path targetDirectory = determineTargetDirectory(mainClass);
                Path cliScript = targetDirectory.getParent().resolve(CLI_SCRIPT_SOURCE).normalize().toAbsolutePath();

                if (Files.exists(cliScript)) {
                    LOGGER.info("Found %s. Will add it to the image.", cliScript);
                } else {
                    LOGGER.info("No CLI script found at %s. Not adding to image", cliScript);
                    cliScript = null;
                }

                S2iBuild s2iBuild = getS2iBuildAnnotation(roundEnv);

                boolean suppliedDockerFile = isDockerFileSupplied(targetDirectory);
                if (!suppliedDockerFile) {
                    generateDockerFile(targetDirectory, cliScript);
                }

                addDefaults(mainClass, cliScript != null, ports, !suppliedDockerFile, s2iBuild);
            }
        }

        return false;
    }

    private void addDefaults(Element mainClass, boolean cliScriptAdded, Port[] ports, boolean useGenerateDockerFile,
                             S2iBuild s2iBuild) {
        Map<String, Object> inputProperties = new HashMap<>();

        if (cliScriptAdded) {
            inputProperties.put(getEnvVarPrefix() + "env-vars[0].name", CLI_LAUNCH_SCRIPT_VAR);
            inputProperties.put(getEnvVarPrefix() + "env-vars[0].value", CLI_SCRIPT_IN_IMAGE);
        }
        
        inputProperties.put(getPortPrefix() + "ports[0].name", "http");
        inputProperties.put(getPortPrefix() + "ports[0].containerPort", "8080");
        String nodePort = getNodePort("http", ports);
        if ( nodePort != null) {
            inputProperties.put(getPortPrefix() + "ports[0].nodePort", nodePort);
        }
        inputProperties.put(getPortPrefix() + "ports[1].name", "admin");
        inputProperties.put(getPortPrefix() + "ports[1].containerPort", "9990");
        nodePort = getNodePort("admin", ports);
        if ( nodePort != null) {
            inputProperties.put(getPortPrefix() + "ports[1].nodePort", nodePort);
        }

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

        if (s2iBuild == null) {
            // If the annotation is used we let that take precedence
            // Let dekorate handle this since the test is set up differently from expected
            setPropertyWithDefault(inputProperties, "dekorate.s2i.enabled", "false");
        }

        if (useGenerateDockerFile) {
            // We want to generate our DockerFile, so override the location
            inputProperties.put("dekorate.docker.docker-file", GENERATED_DOCKER_FILE_LOCATION);
        }

        addAddtionalProperties(inputProperties, s2iBuild);

        Map<String, Object> properties = Maps.fromProperties(inputProperties);

        getSession().addPropertyConfiguration(properties);

    }

    private void setPropertyWithDefault(Map<String, Object> inputProperties, String propertyName, String defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null) {
            value = defaultValue;
        }
        inputProperties.put(propertyName, value);
    }

    private String getNodePort(String name, Port[] ports) {
        for (Port port : ports) {
            if(name.equals(port.name())) {
                if(port.nodePort() != 0) {
                    return ""+port.nodePort();
                }
            }
        }
        return null;
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



        String imageName = System.getProperty("wildfly.cloud.test.base.image.name");
        if (imageName == null || imageName.trim().isBlank()) {
            throw new IllegalStateException("No image name set via the 'wildfly.cloud.test.base.image.name' property in the test module pom");
        }

        List<String> lines = new ArrayList<>();
        lines.add("FROM " + imageName);
        lines.add("COPY --chown=jboss:root target/ROOT.war $JBOSS_HOME/standalone/deployments");
        lines.add("RUN chmod -R ug+rwX $JBOSS_HOME");


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

    private boolean isDockerFileSupplied(Path targetDirectory) {
        Path dockerFile = targetDirectory.getParent().resolve(DOCKER_FILE);
        if (!Files.exists(dockerFile)) {
            return false;
        }
        // Check if there is any content
        final List<String> lines;
        try {
            lines = Files.readAllLines(dockerFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 0 && !line.startsWith("#")) {
                // Line contained some instructions, so consider the user wants to override it
                return true;
            }
        }
        return false;
    }

    private S2iBuild getS2iBuildAnnotation(RoundEnvironment roundEnv) {
        TypeElement dockerBuild = processingEnv.getElementUtils().getTypeElement(S2iBuild.class.getName());
        Set<? extends Element> s2iBuildClasses = roundEnv.getElementsAnnotatedWith(dockerBuild);
        if (s2iBuildClasses.size() > 1) {
            Set<String> annotatedClasses = new HashSet<>();
            for (Element annotatedClass : s2iBuildClasses) {
                annotatedClasses.add(annotatedClass.toString());
            }
            String err = "Found more than one class annotated with @S2iBuild: " + s2iBuildClasses;
            LOGGER.error(err);
            throw new IllegalStateException(err);
        }
        if (s2iBuildClasses.size() == 1) {
            return s2iBuildClasses.iterator().next().getAnnotation(S2iBuild.class);
        }
        return null;
    }
}
