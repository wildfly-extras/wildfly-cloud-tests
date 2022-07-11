FROM wildfly-cloud-test-image/image-cloud-server:latest
COPY --chown=jboss:root target/ROOT.war $JBOSS_HOME/standalone/deployments
RUN chmod -R ug+rwX $JBOSS_HOME