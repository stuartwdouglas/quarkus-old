package org.jboss.shamrock.openssl;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.builditem.RuntimeInitializedClassBuildItem;

@BuildStep
public class OpenSSLResourceProcessor implements BuildProcessingStep {


    @BuildResource
    BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit;

    @Override
    public void build() throws Exception {
        runtimeInit.produce(new RuntimeInitializedClassBuildItem("org.wildfly.openssl.OpenSSLEngine"));
    }
}
