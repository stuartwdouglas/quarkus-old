package org.jboss.shamrock.health;

import org.jboss.shamrock.annotations.BuildProcessor;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.ShamrockConfig;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.health.runtime.HealthServlet;
import org.jboss.shamrock.undertow.ServletData;

import io.smallrye.health.SmallRyeHealthReporter;

@BuildProcessor
class HealthProcessor implements BuildProcessingStep {

    @BuildResource
    BuildProducer<AdditionalBeanBuildItem> additionalBeans;

    @BuildResource
    ShamrockConfig config;

    @BuildResource
    BuildProducer<ServletData> servlets;

    @Override
    public void build() throws Exception {

        ServletData servletData = new ServletData("health", HealthServlet.class.getName());
        servletData.getMapings().add(config.getConfig("health.path", "/health"));
        servlets.produce(servletData);
        additionalBeans.produce(new AdditionalBeanBuildItem(SmallRyeHealthReporter.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(HealthServlet.class));
    }
}
