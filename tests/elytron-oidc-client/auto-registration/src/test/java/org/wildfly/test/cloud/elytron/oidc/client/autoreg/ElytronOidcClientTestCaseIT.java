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
package org.wildfly.test.cloud.elytron.oidc.client.autoreg;

import io.dekorate.testing.annotation.Inject;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CookieSpecRegistries;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BasicDomainHandler;
import org.apache.http.impl.cookie.BasicExpiresHandler;
import org.apache.http.impl.cookie.BasicMaxAgeHandler;
import org.apache.http.impl.cookie.BasicPathHandler;
import org.apache.http.impl.cookie.BasicSecureHandler;
import org.apache.http.impl.cookie.RFC6265CookieSpec;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.test.cloud.common.WildFlyCloudTestCase;
import org.wildfly.test.cloud.common.WildFlyKubernetesIntegrationTest;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@WildFlyKubernetesIntegrationTest
public class ElytronOidcClientTestCaseIT extends WildFlyCloudTestCase {

    private static final String KEYCLOAK_USERNAME = "username";
    private static final String KEYCLOAK_PASSWORD = "password";
    @Inject
    private KubernetesClient client;

    @Inject
    private KubernetesList list;

    @Test
    public void checkKeycloak() throws Exception {
        
        // TODO, configure keycloak realm, ...
        
        Integer appPort = null;
        for (ServicePort sp : client.services().withName(getHelper().getContainerName()).get().getSpec().getPorts()) {
            if ("http".equals(sp.getName())) {
                appPort = sp.getNodePort();
                break;
            }
        }
        Assertions.assertNotNull(appPort, "nodePort of application is not found in service");
        loginToApp("demo", "demo", new URL("http://" + client.getMasterUrl().getHost() + ":" + appPort + "/secured"));
    }

    public static void loginToApp(String username, String password, URL requestUri) throws Exception {
        CookieStore store = new BasicCookieStore();
        HttpClient httpClient = promiscuousCookieHttpClientBuilder()
                .setDefaultCookieStore(store)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();
        HttpGet getMethod = new HttpGet(requestUri.toURI());
        HttpContext context = new BasicHttpContext();
        HttpResponse response = httpClient.execute(getMethod, context);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            Form keycloakLoginForm = new Form(response);
            HttpResponse afterLoginClickResponse = simulateClickingOnButton(httpClient, keycloakLoginForm, username, password, "Sign In");
            afterLoginClickResponse.getEntity().getContent();
            Assertions.assertEquals(200, afterLoginClickResponse.getStatusLine().getStatusCode());
            String responseString = new BasicResponseHandler().handleResponse(afterLoginClickResponse);
            Assertions.assertTrue(responseString.contains(SecuredServlet.RESPONSE_BODY));
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    public static HttpClientBuilder promiscuousCookieHttpClientBuilder() {
        HttpClientBuilder builder = HttpClients.custom();

        RegistryBuilder<CookieSpecProvider> registryBuilder = CookieSpecRegistries.createDefaultBuilder();
        Registry<CookieSpecProvider> promiscuousCookieSpecRegistry = registryBuilder.register("promiscuous", new PromiscuousCookieSpecProvider()).build();
        builder.setDefaultCookieSpecRegistry(promiscuousCookieSpecRegistry);

        RequestConfig requestConfig = RequestConfig.custom().setCookieSpec("promiscuous").build();
        builder.setDefaultRequestConfig(requestConfig);

        builder.setDefaultCookieStore(new PromiscuousCookieStore());

        return builder;
    }

    private static class PromiscuousCookieSpecProvider implements CookieSpecProvider {

        @Override
        public CookieSpec create(HttpContext context) {
            return new PromiscuousCookieSpec();
        }
    }

    private static class PromiscuousCookieSpec extends RFC6265CookieSpec {

        private PromiscuousCookieSpec() {
            super(
                    new BasicPathHandler(),
                    new BasicDomainHandler() {
                @Override
                public boolean match(Cookie cookie, CookieOrigin origin) {
                    return true;
                }

                @Override
                public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
                    // Accept any
                }
            },
                    new BasicMaxAgeHandler(),
                    new BasicSecureHandler(),
                    new BasicExpiresHandler(new String[]{
                DateUtils.PATTERN_RFC1123,
                DateUtils.PATTERN_RFC1036,
                DateUtils.PATTERN_ASCTIME
            })
            );
        }
    }

    private static class PromiscuousCookieStore extends BasicCookieStore {

        @Override
        public synchronized void addCookie(Cookie cookie) {
            ((BasicClientCookie) cookie).setDomain(null);
            super.addCookie(cookie);
        }
    }

    public static final class Form {

        static final String NAME = "name",
                VALUE = "value",
                INPUT = "input",
                TYPE = "type",
                ACTION = "action",
                FORM = "form";

        final HttpResponse response;
        final String action;
        final List<Input> inputFields = new LinkedList<>();

        public Form(HttpResponse response) throws IOException {
            this.response = response;
            final String responseString = new BasicResponseHandler().handleResponse(response);
            System.out.println("RESPONSE " + responseString);
            final Document doc = Jsoup.parse(responseString);
            final Element form = doc.select(FORM).first();
            this.action = form.attr(ACTION);
            for (Element input : form.select(INPUT)) {
                Input.Type type = null;
                switch (input.attr(TYPE)) {
                    case "submit":
                        type = Input.Type.SUBMIT;
                        break;
                    case "hidden":
                        type = Input.Type.HIDDEN;
                        break;
                }
                inputFields.add(new Input(input.attr(NAME), input.attr(VALUE), type));
            }
        }

        public String getAction() {
            return action;
        }

        public List<Input> getInputFields() {
            return inputFields;
        }
    }

    private static final class Input {

        final String name, value;
        final Input.Type type;

        public Input(String name, String value, Input.Type type) {
            this.name = name;
            this.value = value;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public enum Type {
            HIDDEN, SUBMIT
        }
    }

    private static HttpResponse simulateClickingOnButton(HttpClient client, Form form, String username, String password, String buttonValue) throws IOException {
        final URL url = new URL(form.getAction());
        final HttpPost request = new HttpPost(url.toString());
        final List<NameValuePair> params = new LinkedList<>();
        for (Input input : form.getInputFields()) {
            if (input.type == Input.Type.HIDDEN
                    || (input.type == Input.Type.SUBMIT && input.getValue().equals(buttonValue))) {
                params.add(new BasicNameValuePair(input.getName(), input.getValue()));
            } else if (input.getName().equals(KEYCLOAK_USERNAME)) {
                params.add(new BasicNameValuePair(input.getName(), username));
            } else if (input.getName().equals(KEYCLOAK_PASSWORD)) {
                params.add(new BasicNameValuePair(input.getName(), password));
            }
        }
        request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        return client.execute(request);
    }
}
