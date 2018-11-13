package org.jboss.shamrock.deployment.steps;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.deployment.cdi.BeanDeployment;

public class BeanDeploymentProducer {

    @BuildStep
    public BeanDeployment build() throws Exception {
        return new BeanDeployment();
    }
}
