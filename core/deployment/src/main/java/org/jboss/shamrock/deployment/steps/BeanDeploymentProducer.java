package org.jboss.shamrock.deployment.steps;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.BeanDeployment;
import org.jboss.shamrock.deployment.BuildProcessingStep;

@BuildStep
public class BeanDeploymentProducer implements BuildProcessingStep {

    @BuildResource
    BuildProducer<BeanDeployment> producer;

    @Override
    public void build() throws Exception {
        producer.produce(new BeanDeployment());
    }
}
