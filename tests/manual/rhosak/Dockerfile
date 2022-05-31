# Base image built from the images/cloud-server module
FROM wildfly-cloud-test-image/image-microprofile-reactive-messaging-kafka:latest
COPY --chown=jboss:root src/main/docker/initialize-server.cli src/main/docker/postconfigure.sh $JBOSS_HOME/extensions/
COPY --chown=jboss:root target/ROOT.war $JBOSS_HOME/standalone/deployments