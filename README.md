# WildFly Cloud Testsuite

Cloud test suite for WildFly

## Usage

### Prerequisites

#### Prerequisites for Kubernetes

You need a local Kubernetes cluster (minikube) with a local Docker registry at `localhost:5000`. CI uses Docker; instructions for Podman are also provided below.

##### Using Docker (matches CI)

1. Install [Docker](https://docs.docker.com/get-docker/), [`kubectl`](https://kubernetes.io/docs/tasks/tools/), and [`minikube`](https://minikube.sigs.k8s.io/docs/start/).

2. Start minikube:
   ```shell
   minikube start --driver=docker --container-runtime=containerd --memory='4gb' --cpus='2'
   ```

3. Start a local Docker registry:
   ```shell
   docker run -d -p 5000:5000 --restart=always --name local-registry \
     -e REGISTRY_STORAGE_DELETE_ENABLED=true \
     registry:2
   ```

4. Connect the registry to minikube's network so pods can pull images:
   ```shell
   MINIKUBE_NETWORK=$(docker inspect minikube --format='{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}')
   docker network connect "$MINIKUBE_NETWORK" local-registry
   ```

5. Configure containerd inside minikube to resolve `localhost:5000` via the registry container's IP. Without this, pods that reference `localhost:5000/...` images would look on the node's own loopback and fail with `ImagePullBackOff`:
   ```shell
   REGISTRY_IP=$(docker inspect local-registry \
     --format="{{.NetworkSettings.Networks.${MINIKUBE_NETWORK}.IPAddress}}")
   minikube ssh "sudo mkdir -p /etc/containerd/certs.d/localhost:5000"
   minikube ssh "printf '[host.\"http://${REGISTRY_IP}:5000\"]\n  capabilities = [\"pull\", \"resolve\"]\n' \
     | sudo tee /etc/containerd/certs.d/localhost:5000/hosts.toml"
   minikube ssh "sudo systemctl restart containerd"
   ```
   Wait a few seconds for containerd to restart before running tests.

6. Verify the setup:
   ```shell
   # Registry is accessible from the host
   curl -s http://localhost:5000/v2/_catalog
   # Registry is accessible from inside minikube
   minikube ssh "curl -f http://${REGISTRY_IP}:5000/v2/"
   ```

##### Using Podman

These instructions use a standalone registry container, the same approach as the Docker instructions above. The minikube registry addon does not work reliably with Podman on macOS/Windows because `--network=host` maps to the Podman VM's network rather than the host machine's.

1. **Platform-specific prerequisites:**

   **macOS/Windows only:** You need a running Podman machine with at least 8 GB of memory and 4 CPUs (minikube, the registry, and test workloads all share these resources). If you don't have one yet:
   ```shell
   podman machine init --memory 8192 --cpus 4
   podman machine start
   ```

   Next, configure `localhost:5000` as an insecure (HTTP) registry inside the Podman machine. This must be done **before** starting minikube. Check whether the configuration already exists:
   ```shell
   podman machine ssh --username root cat /etc/containers/registries.conf
   ```
   If the output does not contain an entry for `localhost:5000`, add one:
   ```shell
   podman machine ssh --username root tee -a /etc/containers/registries.conf <<'EOF'

   [[registry]]
   location="localhost:5000"
   insecure=true
   EOF
   ```
   Verify it was written:
   ```shell
   podman machine ssh --username root cat /etc/containers/registries.conf
   ```
   Then restart the Podman machine so the configuration takes effect:
   ```shell
   podman machine stop && podman machine start
   ```

   > **Note:** On macOS, AirPlay Receiver sometimes binds to port 5000. If you get connection errors later, disable it in **System Settings > General > AirDrop & Handoff > AirPlay Receiver**.

   **Linux only:** Enable the podman socket and set `DOCKER_HOST` so minikube can find it:
   ```shell
   systemctl --user enable --now podman.socket
   export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
   ```
   You may also need to register `localhost:5000` as an insecure registry. Add the following to `/etc/containers/registries.conf` (recent Podman versions allow localhost by default, but some distributions require this):
   ```toml
   [[registry]]
   location="localhost:5000"
   insecure=true
   ```

2. Start minikube with the podman driver:
   ```shell
   minikube start --driver=podman --container-runtime=containerd --memory='4gb' --cpus='2'
   ```

3. Start a local registry:
   ```shell
   podman run -d -p 5000:5000 --rm --name local-registry \
     -e REGISTRY_STORAGE_DELETE_ENABLED=true \
     registry:2
   ```

4. Connect the registry to minikube's network so pods can pull images:
   ```shell
   MINIKUBE_NETWORK=$(podman inspect minikube --format='{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}')
   podman network connect "$MINIKUBE_NETWORK" local-registry
   ```

5. Configure containerd inside minikube to resolve `localhost:5000` via the registry container's IP. Without this, pods that reference `localhost:5000/...` images would look on the node's own loopback and fail with `ImagePullBackOff`:
   ```shell
   REGISTRY_IP=$(podman inspect local-registry \
     --format="{{.NetworkSettings.Networks.${MINIKUBE_NETWORK}.IPAddress}}")
   minikube ssh "sudo mkdir -p /etc/containerd/certs.d/localhost:5000"
   minikube ssh "printf '[host.\"http://${REGISTRY_IP}:5000\"]\n  capabilities = [\"pull\", \"resolve\"]\n' \
     | sudo tee /etc/containerd/certs.d/localhost:5000/hosts.toml"
   minikube ssh "sudo systemctl restart containerd"
   ```
   Wait a few seconds for containerd to restart before running tests.

6. Verify the setup:
   ```shell
   # Registry is accessible from the host
   curl -s http://localhost:5000/v2/_catalog
   # Registry is accessible from inside minikube
   minikube ssh "curl -f http://${REGISTRY_IP}:5000/v2/"
   ```

The WildFly Maven Plugin uses `podman` by default for building container images, so no `-Dwildfly.image.container-runtime` override is needed when running the tests with Podman.

> **Known limitation:** The OIDC (elytron-oidc-client) test uses NodePort services to handle the browser-like login flow between WildFly and Keycloak. On macOS with Podman, the minikube node IP (`minikube ip`) is inside the Podman VM and not routable from the host, so the test will fail with a connection timeout. This test passes on CI (Docker on Linux) where the minikube IP is directly reachable.

### Run the tests

The `kubernetes-tests` profile is active by default, and runs the tests tagged with `@Tag(WildflyTags.KUBERNETES)`. These tests target Kubernetes, running on Minikube as outlined above.

#### Kubernetes tests
```shell
mvn clean verify -Pkubernetes-tests
```

By default, the WildFly Maven Plugin uses `podman` to build container images. To use `docker` instead:
```shell
mvn clean verify -Pkubernetes-tests -Dwildfly.image.container-runtime=docker
```

#### CI mode
On CI, set `CLOUD_TESTS_CI=1` to enable registry cleanup between tests (reduces disk usage):
```shell
CLOUD_TESTS_CI=1 mvn clean verify -Pkubernetes-tests -Dwildfly.image.container-runtime=docker
```

### Obtaining pod logs and standalone.xml contents
If a test fails, pod logs and server configuration are automatically dumped to the console.


## Architecture

Each test module is self-contained:
1. **Application code** in `src/main/java` — a plain WAR application (JAX-RS, Servlet, etc.)
2. **Deployment YAML** in `src/main/kubernetes/wildfly-deployment.yml` — static Kubernetes Deployment + Service
3. **WildFly Maven Plugin** in the module's `pom.xml` — provisions the server and builds a container image via `wildfly:image`
4. **CLI scripts** (optional) in `src/main/cli-script/init.cli` — JBoss CLI commands run during server provisioning
5. **Test code** in `src/test/java` — a JUnit 5 integration test using `@WildFlyKubernetesIntegrationTest`
6. **Test overrides** (optional) in `src/test/resources/overrides/` — override script and prerequisite Kubernetes YAMLs

The test lifecycle is:
- `mvn package` builds the WAR, provisions a WildFly server with the configured Galleon layers, and builds a container image
- The JUnit 5 extension (`WildFlyKubernetesExtension`) calls `.github/scripts/setup-test.sh` in `@BeforeAll`, which tags/pushes the image to the local registry and applies the deployment YAML
- Tests run against the deployed application using port-forwarding
- The extension calls `.github/scripts/teardown-test.sh` in `@AfterAll` to clean up


## Adding tests

### Adding Kubernetes tests
To add a test, create a new Maven module under `tests/`. We use a few levels of folders to group tests by area of functionality.

We use the [Failsafe plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/) to run these tests:
* `src/main/java` and `src/main/webapp` contain the application being tested
* `src/test/java` contains the test, which works as a client test

#### 1. Create the application class

A plain JAX-RS or Servlet application — no special annotations needed beyond the standard ones:

```java
@ApplicationPath("")
public class MyApp extends Application {
}
```

#### 2. Create the deployment YAML

Create `src/main/kubernetes/wildfly-deployment.yml` with a Deployment and Service. The key requirements:
- The `app.kubernetes.io/name` label must match the `<name>` in the WildFly Maven Plugin config
- The image must be `localhost:5000/<artifactId>:latest` (the WildFly Maven Plugin names images after the Maven artifactId)
- Include ports `8080` (http) and `9990` (admin)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-test
  labels:
    app.kubernetes.io/name: my-test
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: my-test
  template:
    metadata:
      labels:
        app.kubernetes.io/name: my-test
    spec:
      containers:
        - name: my-test
          image: localhost:5000/wildfly-cloud-tests-my-test:latest
          imagePullPolicy: Always
          ports:
            - name: http
              containerPort: 8080
            - name: admin
              containerPort: 9990
---
apiVersion: v1
kind: Service
metadata:
  name: my-test
spec:
  selector:
    app.kubernetes.io/name: my-test
  ports:
    - name: http
      port: 8080
      targetPort: 8080
    - name: admin
      port: 9990
      targetPort: 9990
```

#### 3. Configure the WildFly Maven Plugin

Add to the module's `pom.xml`:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.wildfly.plugins</groupId>
            <artifactId>wildfly-maven-plugin</artifactId>
            <configuration>
                <name>ROOT.war</name>
                <container-runtime>${wildfly.image.container-runtime}</container-runtime>
                <feature-packs>
                    <feature-pack>
                        <location>${server.feature.pack.gav}</location>
                    </feature-pack>
                    <feature-pack>
                        <location>${cloud.feature.pack.gav}</location>
                    </feature-pack>
                </feature-packs>
                <layers>
                    <layer>${cloud.feature.pack.default.config.layer}</layer>
                    <!-- Add additional layers as needed -->
                </layers>
                <galleon-options>
                    <jboss-fork-embedded>${plugin.fork.embedded}</jboss-fork-embedded>
                </galleon-options>
            </configuration>
            <executions>
                <execution>
                    <id>provision-server</id>
                    <phase>package</phase>
                    <goals><goal>package</goal></goals>
                </execution>
                <execution>
                    <id>build-image</id>
                    <phase>package</phase>
                    <goals><goal>image</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

The `<name>` must match the application name used in the deployment YAML and labels.

#### 4. Write the test class

```java
@WildFlyKubernetesIntegrationTest
public class MyTestIT extends WildFlyCloudTestCase {

    @Test
    public void smokeTest() throws Exception {
        getHelper().doWithWebPortForward("", url -> {
            Response r = RestAssured.get(url);
            assertEquals(200, r.getStatusCode());
            return null;
        });
    }
}
```

The `@WildFlyKubernetesIntegrationTest` annotation includes `@Tag("Kubernetes")`, so no separate `@Tag` is needed.

`WildFlyCloudTestCase` provides `getHelper()` which returns a `TestHelper` with methods for:
- `doWithWebPortForward(path, action)` — port-forward to the pod and execute an action
- `executeCLICommands(commands...)` — run JBoss CLI commands in the pod
- `readFile(path)` — read a file from the pod
- `runCommand(command, expectOutput)` — run a bash command in the pod
- `kubectl()` — access the `KubectlHelper` for direct kubectl operations

### Adding prerequisites (databases, operators, etc.)

If your test needs additional Kubernetes resources (databases, message brokers, operators), create an override script at `<module-path>/src/test/resources/overrides/overridable-functions.sh`. Place any prerequisite YAML files alongside the override script in the same `overrides/` directory.

The available functions to override are defined in `.github/scripts/overridable-functions.sh`:

- `applicationName(modulePath)` — the application name (default: last directory component)
- `namespace(appName)` — the namespace to deploy into (default: current context namespace)
- `installPrerequisites(appName)` — install additional resources before the app
- `cleanPrerequisites(appName)` — clean up additional resources after the test
- `getDeploymentYaml()` — path to the deployment YAML (default: `src/main/kubernetes/wildfly-deployment.yml`)
- `waitForReadiness(appName)` — wait for the deployment to be ready
- `preprocessYaml()` — filter to transform the deployment YAML (e.g., placeholder substitution)
- `containerCommand()` — the container runtime command (default: auto-detect docker/podman)

Example override for a test that needs a PostgreSQL database:
```bash
function installPrerequisites() {
  local app="${1}"
  kubectl apply -f src/test/resources/overrides/kubernetes.yml
  kubectl wait --for=condition=Available deployment/postgres --timeout=120s
}

function cleanPrerequisites() {
  local app="${1}"
  kubectl delete -f src/test/resources/overrides/kubernetes.yml --ignore-not-found=true
}
```

### 'Manual' tests
The tests in the `tests/manual` folder will not run automatically, as they need external
systems to be set up before running. See the README for each test for how to run them.
