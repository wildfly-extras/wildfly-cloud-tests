/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.cloud.tests.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ArchiveToDockerImageUtil {

    private static final String IMAGE_DEPLOYMENTS_PATH = "/opt/server/standalone/deployments/";
    private static final String USER = "jboss:root";


    private final String baseImageName;
    private final String targetImageName;
    private final Archive archive;

    public ArchiveToDockerImageUtil(String baseImageName, String targetImageName, Archive archive) {
        this.baseImageName = baseImageName;
        this.targetImageName = targetImageName;
        this.archive = archive;
    }

    public void createImageWithArchiveDeployment() throws Exception {
        Path tempDirectory = Files.createTempDirectory("tests");
        try {
            String archiveName = writeArchiveToTempDirectory(tempDirectory);
            createDockerFileInTempDirectory(tempDirectory, archiveName);
            buildDockerImage(tempDirectory);
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    public void cleanupImage() throws Exception {

    }

    private String writeArchiveToTempDirectory(Path dir) {
        String archiveName = archive.getName();
        archive.as(ZipExporter.class).exportTo(dir.resolve(archiveName).toFile(), true);
        return archiveName;
    }

    private void createDockerFileInTempDirectory(Path dir, String archiveName) throws Exception {
        Path dockerFile = dir.resolve("Dockerfile");
        Files.createFile(dockerFile);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dockerFile.toFile()))) {
            writer.write(String.format("FROM %s\n", baseImageName));
            writer.write(String.format("COPY --chown=%s %s %s\n", USER, archiveName, IMAGE_DEPLOYMENTS_PATH + archiveName));
        }
    }

    private void buildDockerImage(Path directory) throws IOException, InterruptedException {
        ProcessBuilder pm = new ProcessBuilder()
                .directory(directory.toFile())
                .command("docker", "build", "-t", targetImageName, ".");

        Process proc = pm.start();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(new ProcessStreamReader(proc.getInputStream(), System.out));
        executor.submit(new ProcessStreamReader(proc.getErrorStream(), System.err));

        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    String.format(
                            "Could not create image %s from image %s and deployment %s. Check the error output for more details",
                            baseImageName, targetImageName, archive.getName()));
        }

        executor.shutdown();
    }

    private class ProcessStreamReader implements Runnable {
        private final InputStream in;
        private final PrintStream out;

        ProcessStreamReader(InputStream in, PrintStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line = reader.readLine();
                while (line != null) {
                    out.println(line);
                    line = reader.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return super.postVisitDirectory(dir, exc);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return super.visitFile(file, attrs);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        // TODO delete this, it is just here to test my WIP
        WebArchive wa = ShrinkWrap.create(WebArchive.class, "myapp.war")
                .addClass(ArchiveToDockerImageUtil.class);
        ArchiveToDockerImageUtil util = new ArchiveToDockerImageUtil("wildfly/test-cloud-server:latest", "wildfly/test-app-server", wa);
        util.createImageWithArchiveDeployment();
    }
}
