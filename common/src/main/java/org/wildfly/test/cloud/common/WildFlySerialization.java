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

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dekorate.utils.Serialization;
import io.dekorate.utils.Strings;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WildFlySerialization extends Serialization {

    static {
        // Delete this when https://github.com/dekorateio/dekorate/pull/1141 is merged and released
        try {
            Class<Serialization> clazz = Serialization.class;
            hackMapper(clazz, "JSON_MAPPER");
            hackMapper(clazz, "YAML_MAPPER");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static void hackMapper(Class clazz, String name) throws Exception {
        // Delete this when https://github.com/dekorateio/dekorate/pull/1141 is merged and released
        Field jsonMapper = clazz.getDeclaredField(name);
        jsonMapper.setAccessible(true);
        try {
            ObjectMapper jom = (ObjectMapper) jsonMapper.get(null);
            jom.findAndRegisterModules();
        } finally {
            jsonMapper.setAccessible(false);
        }
    }


    // Override to work around https://github.com/dekorateio/dekorate/pull/922
    public static KubernetesList unmarshalAsList(InputStream is) {
        String content = Strings.read(is);
        String[] parts = splitDocument(content);
        System.out.println("Found " + parts.length + " entries");
        List<HasMetadata> items = new ArrayList<>();
        for (String part : parts) {
            if (part.trim().isEmpty()) {
                continue;
            }
            Object resource = unmarshal(part.trim());
            if (resource instanceof KubernetesList) {
                items.addAll(((KubernetesList) resource).getItems());
            } else if (resource instanceof HasMetadata) {
                items.add((HasMetadata) resource);
            } else if (resource instanceof HasMetadata[]) {
                Arrays.stream((HasMetadata[]) resource).forEach(r -> items.add(r));
            }
        }
        return new KubernetesListBuilder().withItems(items).build();
    }


    private static String[] splitDocument(String aSpecFile) {
        List<String> documents = new ArrayList();
        String[] lines = aSpecFile.split("\\r?\\n");
        int nLine = 0;

        StringBuilder builder;
        for(builder = new StringBuilder(); nLine < lines.length; ++nLine) {
            if ((lines[nLine].length() < "---".length() || lines[nLine].substring(0, "---".length()).equals("---")) && lines[nLine].length() >= "---".length()) {
                documents.add(builder.toString());
                builder.setLength(0);

                for(int i = 0; i <= nLine; ++i) {
                    builder.append(System.lineSeparator());
                }
            } else {
                builder.append(lines[nLine] + System.lineSeparator());
            }
        }

        if (!builder.toString().isEmpty()) {
            documents.add(builder.toString());
        }

        return (String[])documents.toArray(new String[documents.size()]);
    }

}
