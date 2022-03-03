# WildFly Cloud Testsuite

Cloud test suite for WildFly

## Usage

### Prerequisites

* Install `docker` and `kubectl`
* Install and start `minikube`
* Install https://minikube.sigs.k8s.io/docs/handbook/registry/[Minikube registry] 

----
minikube addons enable registry
----

* On Mac, run:

----
docker run --rm -it --network=host alpine ash -c "apk add socat && socat TCP-LISTEN:5000,reuseaddr,fork TCP:$(minikube ip):5000"
----

### Run the tests

----
mvn -Pimages clean install
----
By default the tests assume that you are connecting to a registry on `localhost:5000`, as we set that up earlier. If you wish to override the registry, you can use the following system properties:
* `wildfly.cloud.test.docker.host` - to override the host
* `wildfly.cloud.test.docker.port` - to override the port 
* `Ddekorate.docker.registry` - to override the whole `<host>:<port>` in one go. 

`-Pimages` causes the images defined in the `/images` sub-directories to be built. To save time, when developing locally, once you have built your images, omit `-Pimages`. See the [Adding images](#adding-images) section for more details about the images.

## Adding tests
To add a test, at present, you need to create a new Maven module under `tests`. Note that we use a few levels of folders to group tests according to area of functionality.

We use the [Failsafe plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/) to run these tests. Thus:

* the `src/main/java`, `src/main/resources` and `src/main/webapp` folders will contain the application being tested.
* the `src/test/java` folder contains the test, which works as a client test (similar to `@RunAsClient` in Arquillian).

Note that we use the [dekorate.io](https://dekorate.io) framework for the tests. This means using `@KubernetesApplication` to define the application. dekorate.io's annotation processor will create the relevant YAML files to deploy the application to Kubernetes.

On the test side, we use dekorate.io's Junit 5 test based framework to run the tests. To enable this, add the `@KubernetesIntegrationTest` annotation to your test class. 

Additionally, you need a `Dockerfile` in the root of the Maven module containing your test. This is quite simple:
```
# Choose the server image to use, as mentioned in the `Adding images` section
FROM wildfly-cloud-test-image/image-cloud-server:latest
# Copy the built application into the WildFly distribution in the image 
COPY --chown=jboss:root target/ROOT.war $JBOSS_HOME/standalone/deployments
```

dekorate.io allows you to inject [`KubernetesClient`](https://github.com/fabric8io/kubernetes-client/blob/master/kubernetes-client-api/src/main/java/io/fabric8/kubernetes/client/KubernetesClient.java), [`KubernetesList`](https://github.com/fabric8io/kubernetes-client/blob/master/kubernetes-model-generator/kubernetes-model-core/src/main/java/io/fabric8/kubernetes/api/model/KubernetesList.java) (for Kubernetes resources) and [`Pod`](https://github.com/fabric8io/kubernetes-client/blob/master/kubernetes-model-generator/kubernetes-model-core/src/generated/java/io/fabric8/kubernetes/api/model/Pod.java) instances into your test. 

Additionally, the WildFly Cloud Tests framework, allows you to inject an instance of [`TestHelper`](common/src/main/java/org/wildfly/test/cloud/common/TestHelper.java) initialised for the test being run. This contains methods to run actions such as REST calls using port forwarding to the relevant pods.



## Adding images
If you need a server with different layers from the already existing ones, you need to add a new Maven module under the `images/` directory. Simply choose the layers you wish to provision your server with in the `wildfly-maven-plugin` plugin section in the module `pom.xml`, and the [parent pom](images/pom.xml) will take care of the rest. See any of the existing poms under `images/` for a fuller example.

The name of the image becomes `wildfly-cloud-test-image/<artifact-id>:latest` where `<artifact-id>` is the Maven artifactId of the pom creating the image. The image simply contains an empty server, with no deployments. The tests create images containing the deployments being tested from this image.

Once you have added a new image, add it to:
* The `dependencyManagement` section of the root pom
* The `dependencies` section of [`tests/pom.xml`](tests/pom.xml)

The above ensures that the image will be built before running the tests.