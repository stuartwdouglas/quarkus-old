package org.jboss.shamrock.health;

import javax.inject.Inject;

import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.ArchiveContext;
import org.jboss.shamrock.deployment.BeanDeployment;
import org.jboss.shamrock.deployment.ProcessorContext;
import org.jboss.shamrock.deployment.ResourceProcessor;
import org.jboss.shamrock.deployment.ShamrockConfig;
import org.jboss.shamrock.health.runtime.HealthServlet;
import org.jboss.shamrock.undertow.ServletData;

import io.smallrye.health.SmallRyeHealthReporter;

class HealthProcessor implements ResourceProcessor {

    @Inject
    private BeanDeployment beanDeployment;

    @Inject
    private ShamrockConfig config;


    @BuildResource
    BuildProducer<ServletData> servlets;

    @Override
    public void process(ArchiveContext archiveContext, ProcessorContext processorContext) throws Exception {
        ServletData servletData = new ServletData("health", HealthServlet.class.getName());
        servletData.getMapings().add(config.getConfig("health.path", "/health"));
        servlets.produce(servletData);
        beanDeployment.addAdditionalBean(SmallRyeHealthReporter.class);
        beanDeployment.addAdditionalBean(HealthServlet.class);
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
