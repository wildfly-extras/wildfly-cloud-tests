# WildFly Cloud Testsuite

Cloud test suite for WildFly

## Usage

### Prerequisites

* Install `docker` and `kubectl`
* Install and start `minikube`, making sure it has enough memory
````shell
minikube start --memory='4gb'
````
* Install [Minikube registry](https://minikube.sigs.k8s.io/docs/handbook/registry/)
````shell
minikube addons enable registry
````
* In order to push to the minikube registry and expose it on localhost:5000:
````shell
# On Mac:
docker run --rm -it --network=host alpine ash -c "apk add socat && socat TCP-LISTEN:5000,reuseaddr,fork TCP:$(minikube ip):5000"

# On Linux:
kubectl port-forward --namespace kube-system service/registry 5000:80 &

# On Windows:
kubectl port-forward --namespace kube-system service/registry 5000:80
docker run --rm -it --network=host alpine ash -c "apk add socat && socat TCP-LISTEN:5000,reuseaddr,fork TCP:host.docker.internal:5000"
````

### Run the tests

````shell
mvn -Pimages clean install
````
By default the tests assume that you are connecting to a registry on `localhost:5000`,
which we set up earlier. If you wish to override the registry, you can use the 
following system properties:
* `wildfly.cloud.test.docker.host` - to override the host
* `wildfly.cloud.test.docker.port` - to override the port 
* `wildfly.cloud.test.docker.registry` - to override the whole `<host>:<port>` in one go. 

`-Pimages` causes the images defined in the `/images` sub-directories to be built. 
To save time, when developing locally, once you have built your images, 
omit `-Pimages`. 

See the [Adding images](#adding-images) section for more details about the creation of 
the images.

## Adding tests
To add a test, at present, you need to create a new Maven module under `tests`. 
Note that we use a few levels of folders to group tests according to area of 
functionality.

We use the [Failsafe plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/) 
to run these tests. Thus:

* the `src/main/java`, `src/main/resources` and `src/main/webapp` folders will contain the application being tested.
* the `src/test/java` folder contains the test, which works as a client test (similar to `@RunAsClient` in Arquillian).

Note that we use the [dekorate.io](https://dekorate.io) framework with some extensions
for the tests. This means using `@KubernetesApplication` to define the application. 
dekorate.io's annotation processor will create the relevant YAML files to deploy the 
application to Kubernetes. To see the final result that will be deployed to Kubernetes, 
it is saved in `target/classes/META-INF/dekorate/kubernetes.yml`

A minimum `@KubernetesApplication` is:
```java
@KubernetesApplication(imagePullPolicy = Always)
```
Out of the box the application processor of the WildFly Cloud Tests framework adds the typical WildFly ports `8080` and 
`9990`, as well as setting the `SERVER_PUBLIC_BIND_ADDRESS=0.0.0.0` environment variable. So, the
above trimmed down example results in an **effective configuration** of
```java
@KubernetesApplication(
        ports = {
                @Port(name = "web", containerPort = 8080),
                @Port(name = "admin", containerPort = 9990)
        },
        envVars = {
                @Env(name = "SERVER_PUBLIC_BIND_ADDRESS", value = "0.0.0.0")
        },
        imagePullPolicy = Always)
```
Of course more ports and environment variables can be added as needed, but it is not necssary 
to add the above ones.

The `@KubernetesApplication` annotation is used as input for the generated kubernetes.yml.

On the test side, we use dekorate.io's Junit 5 test based framework to run the 
tests. To enable this, add the `@WildFlyKubernetesIntegrationTest` annotation 
to your test class. This contains the same values as dekorate.io's
[`@KubernetesIntegrationTest`](https://github.com/dekorateio/dekorate/blob/2.9.0/testing/kubernetes-junit/src/main/java/io/dekorate/testing/annotation/KubernetesIntegrationTest.java)
as well as some more fields for additional control. These include:
* `namespace` - the namespace to install the application into, if the default namespace is not desired. This applies to both the namespace used for the application, as well as any additional resources needed. Additional resources are covered later in this document.
* `kubernetesResources` - locations of other Kubernetes resources. See this [section](#adding-additional-complex-kubernetes-resources) for more details.

The framework will generate a Dockerfile from the provided information at `target/docker/Dockerfile`.
You must select the name of the image to use (see [Adding Images](#adding-images)) and set it in a property
called `wildfly.cloud.test.base.image.name` in the pom for the Maven module containing your test. 

Note that at the moment,
due to https://github.com/dekorateio/dekorate/issues/1000, you need to add a Dockerfile to the root
of the Maven module containing the test.

dekorate.io allows you to inject 
[`KubernetesClient`](https://github.com/fabric8io/kubernetes-client/blob/master/kubernetes-client-api/src/main/java/io/fabric8/kubernetes/client/KubernetesClient.java),
[`KubernetesList`](https://github.com/fabric8io/kubernetes-client/blob/master/kubernetes-model-generator/kubernetes-model-core/src/main/java/io/fabric8/kubernetes/api/model/KubernetesList.java) (for Kubernetes resources) 
and [`Pod`](https://github.com/fabric8io/kubernetes-client/blob/master/kubernetes-model-generator/kubernetes-model-core/src/generated/java/io/fabric8/kubernetes/api/model/Pod.java)
instances into your test. 

Additionally, the WildFly Cloud Tests framework, allows you to inject an instance
of [`TestHelper`](common/src/main/java/org/wildfly/test/cloud/common/TestHelper.java) 
initialised for the test being run. This contains methods to run actions such as REST
calls using port forwarding to the relevant pods. It also contains methods to invoke CLI
commands (via bash) in the pod.

The [`WildFlyCloudTestCase`](common/src/main/java/org/wildfly/test/cloud/common/WildFlyCloudTestCase.java)
base class is set up to have the `TestHelper` injected, and also waits for the server to properly
start.

## Further customisation of test images
The above works well for simple tests. However, we might need to add config maps, secrets, 
other pods running a database, operators and so on. Also, we might need to run a CLI script 
when preparing the runtime server before we start it.

### Adding a CLI script on startup
Some tests need to adjust the server configuration. To do this add a CLI script at 
`src/main/cli-script/init.cli` and it will be included in the created Docker image and run
when launching the server.

### Adding additional 'simple' Kubernetes resources
What we have seen so far creates an image for the application. If we want to add more resources,
we need to specify those ourselves. If these are simple resources which are installed right
away, we can leverage dekorate's built in mechanism.

We do this in two steps:
* First we create a `src/main/resources/kubernetes/kubernetes.yml` file containing the Kubernetes resources we want to add. Some examples will follow.
* Next we need to point dekorate to the `kubernetes.yml` by specifying `@GeneratorOptions(inputPath = "kubernetes")` on the application class. The `kubernetes` in this case refers to the folder under the `src/resources` directory.

If you do these steps, the contents of the `src/main/resources/kubernetes/kubernetes.yml` will 
be merged with what is output from the dekorate annotations on your test application. To see 
the final result that will be deployed to Kubernetes, it is saved in 
`target/classes/META-INF/dekorate/kubernetes.yml` as mentioned previously.

The following examples show the contents of `src/main/resources/kubernetes/kubernetes.yml` 
to add commonly needed resources. 

Note that if you have more than one resource, you need to wrap them in a Kubernetes list. There 
is a bug in the dekorate parser which prevents us from using the `---` separator which is often
used to define several resources in the yaml file without wrapping in a list.

An example of a Kubernetes list to specify multiple resources:
```yaml
apiVersion: v1
kind: List
items:
  # First resource
  - apiVersion: v1
    kind: ...
    ...
  # Second resource
  - apiVersion: v1
    kind: ...
    ...
```
### Adding additional 'complex' Kubernetes resources
We sometimes need to install operators, or other complicated yaml to provide functionality 
needed by our tests.  Due to the need to wrap these resources in a Kubernetes List, reworking 
these, often third-party, yaml files is not really practical. When using these 'complex'
resources, the WildFly Cloud Test framework deals with deploying them, so there is no need
to wrap the resources in a Kubernetes list.

In other cases, we need to make sure that these other resources are up and running before we 
deploy our application.

An example of defining additional 'complex' resources for your test follows:
````java

@WildFlyKubernetesIntegrationTest(
  kubernetesResources = {
    @KubernetesResource(definitionLocation = "https://example.com/an-operator.yaml"),
    @KubernetesResource(
      definitionLocation = "src/test/container/my-resources.yml",
      additionalResourcesCreated = {
        @Resource(
          type = ResourceType.DEPLOYMENT, 
          name = "installed-behind-the-scenes")
      }
   )
})
public class MyTestIT {
    //...
}
````
The above installs two resources. The location can either be a URL or a file relative to the root 
of the maven module containing the test.

When deploying each yaml file, it waits for all pods and deployments contained in the file to be 
brought up before deploying the next. The test application is deployed once all the additional
resources have been deployed and are ready.

The `@KubernetesResource.additionalResourcesCreated` attribute used in the second entry 
covers a corner case where the yaml file doesn't explicitly list everything that provides the
full functionality of what is being installed. In this case, the resources contained in the 
yaml install a deployment called `installed-behind-the-scenes` which we need to wait for before
this set of resources can be considered ready for use.

#### Adding config maps
The contents of the config map are specified in `src/main/resources/kubernetes/kubernetes.yml` as follows:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-config-map
data:
  ordinal: 500
  config.map.property: "From config map"
```
To mount the config map as a directory, you need the following additions to the 
`@KubernetesApplication` annotation on your application class:
```java
@KubernetesApplication(
        ...
        configMapVolumes = {@ConfigMapVolume(configMapName = "my-config-map", volumeName = "my-config-map", defaultMode = 0666)},
        mounts = {@Mount(name = "my-config-map", path = "/etc/config/my-config-map")})
@GeneratorOptions(inputPath = "kubernetes")        
```
This sets up a config map volume, and mounts it under `/etc/config/my-config-map`. If you don't 
want to do this you can e.g. bind the config map entries to environment variables. See the 
dekorate documentation for more details.

#### Adding secrets
The contents of the secret are specified in `src/main/resources/kubernetes/kubernetes.yml` as 
follows:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: my-secret
type: Opaque
data:
  secret.property: RnJvbSBzZWNyZXQ=
```
The value of `secret.property` is specified by base64 encoding it:
```shell
$echo -n 'From secret' | base64
RnJvbSBzZWNyZXQ=
```
To mount the secret as a directory, you need the following additions to the 
`@KubernetesApplication` annotation on your application class:
```java
@KubernetesApplication(
        ...
        secretVolumes = {@SecretVolume(secretName = "my-secret", volumeName = "my-secret", defaultMode = 0666)},
        mounts = {@Mount(name = "my-secret", path = "/etc/config/my-secret")})
@GeneratorOptions(inputPath = "kubernetes")        
```
This sets up a secret volume, and mounts it under `/etc/config/my-secret`. If you don't want 
to do this you can e.g. bind the secret entries to environment variables. See the dekorate 
documentation for more details.

### Config Placeholder Replacement
In some cases we don't have the full set of information needed at compile time. We can deal with this with 
placeholders and using an implementation of [`ConfigPlaceholderReplacer`](common/src/main/java/org/wildfly/test/cloud/common/ConfigPlaceholderReplacer.java) to deal with the replacement.

Example use:
```java
// Add an env var with a placeholder
@KubernetesApplication(
        envVars = {
                @Env(name = "POD_URL", value = "$URL$")
        },
        imagePullPolicy = Always)
@ApplicationPath("")
public class EnvVarsOverrideApp extends Application {
}


// Add a config placeholder replacement in the test
@WildFlyKubernetesIntegrationTest(
    placeholderReplacements = {
        @ConfigPlaceholderReplacement(
            placeholder = "$URL$", 
            replacer = SimpleReplacer.class)})
public class MyTestCaseIT extends WildFlyCloudTestCase {
    ...
```
The `placeholderReplacements` will inspect every line of both the generated `target/classes/META-INF/dekorate/kubernetes.yml` as well as any resources specified via 
`@WildFlyKubernetesIntegrationTest.kubernetesResources` and call an instance of `SimpleReplacer` when
performing the replacement.

An example implementation of a replacer:
```java
import io.dekorate.testing.WithKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.wildfly.test.cloud.common.ConfigPlaceholderReplacer;

public class SimpleReplacer implements ConfigPlaceholderReplacer, WithKubernetesClient {
    @Override
    public String replace(ExtensionContext context, String placeholder, String line) {
        if (line.contains("$URL$")) {
            KubernetesClient client = getKubernetesClient(context);
            String url = client.getMasterUrl().toExternalForm();
            line = line.replace("$URL$", url);
        }
        return line;
    }
}
```
In this case it will replace the `$URL$` placeholder we used for the value of the `POD_URL` environment variable with a value determined by the Kubernetes client. The `WithKubernetesClient.getKubernetesClient()` method gets the client for us in this case.

### 'Manual' tests
The tests in the `tests/manual` folder will not run automatically, as they need external 
systems to be set up before running. Still, they are good to verify our images work before 
doing releases of them.

See the README for each test for how to run them. Of course if you add such a test, add a README!
Link all tests to the [tests/manual/README.md](tests/manual/README.md) file, along with the profile
required to run it. [tests/manual/README.md](tests/manual/README.md) contains some further
instructions about how to run these tests on CI.

Ideally, each 'manual' test will be runnable on CI. Add instruhctions for setting up secrets and
whatever else is needed to a 'CI Setup' section in the test README, and modify the 
[.github/workflows/wildfly-cloud-tests-callable.yml](.github/workflows/wildfly-cloud-tests-callable.yml) workflow
file to include the test.

## Adding images
If you need a server with different layers from the already existing ones, you need to add a 
new Maven module under the `images/` directory. Simply choose the layers you wish to provision 
your server with in the `wildfly-maven-plugin` plugin section in the module `pom.xml`, and 
the [images parent pom](images/pom.xml) will take care of the rest. See any of the existing 
poms under `images/` for a fuller example.

The name of the image becomes `wildfly-cloud-test-image/<artifact-id>:latest` 
where `<artifact-id>` is the Maven artifactId of the pom creating the image. The image simply 
contains an empty server, with no deployments. The tests create images containing the 
deployments being tested from this image.

Once you have added a new image, add it to:
* The `dependencyManagement` section of the root pom
* The `dependencies` section of [`tests/pom.xml`](tests/pom.xml)

The above ensures that the image will be built before running the tests.

The current philosophy is to have images providing a minimal amount of layers needed for the 
tests. In other words, we will probably/generally not want to provision the full WildFly server.

By default the created images are based on `quay.io/wildfly-snapshots/wildfly-runtime-jdk11-multi-arch:latest`.
If you wish to use another image (e.g. to prevalidate a staged runtime image) you can do that by passing in
the `image.name.wildfly.runtime` system property.

## Testing new runtime images
To test a new runtime image (for example one that is staged before a release), simply pass in the
name of the image in the `image.name.wildfly.runtime` system property.

Also, the GitHub Actions CI job allows you to pass this in as a parameter when manually triggering
the workflow. You will likely need to run such custom jobs in your own fork of the repository
since only admins can trigger workflows.



