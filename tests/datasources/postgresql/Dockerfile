# Base image built from the images/cloud-server module
FROM wildfly-cloud-test-image/image-cloud-server-with-postgresql-datasource:latest
COPY --chown=jboss:root target/ROOT.war $JBOSS_HOME/standalone/deployments