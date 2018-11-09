package org.jboss.shamrock.health;

import java.util.Arrays;
import java.util.List;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.deployment.ShamrockConfig;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.health.runtime.HealthServlet;
import org.jboss.shamrock.undertow.ServletData;

import io.smallrye.health.SmallRyeHealthReporter;


class HealthProcessor {


    @BuildStep
    public ServletData produceServlet(ShamrockConfig config) {
        ServletData servletData = new ServletData("health", HealthServlet.class.getName());
        servletData.getMapings().add(config.getConfig("health.path", "/health"));
        return servletData;
    }

    @BuildStep
    public List<AdditionalBeanBuildItem> produceCdi() {
        return Arrays.asList(
                new AdditionalBeanBuildItem(SmallRyeHealthReporter.class),
                new AdditionalBeanBuildItem(HealthServlet.class));
    }

//    @BuildStep
//    @Optional
//    public StaticInitBuildItem produceBytecode(RecorderContext context,
//                                       HealthConfig config,
//                                       BuildProducer<DeploymentInfoBuildItem> producer) {
//
//        BytecodeRecorder recorder = context.newRecorder();
//        try (UndertowDeploymentTemplate template = recorder.getRecordingProxy(UndertowDeploymentTemplate.class)) {
//
//            DeploymentInfo info = template.newDeploymentInfo();
//            producer.produce(new DeploymentInfoBuildItem(info));
//
//            return new StaticInitBuildItem(recorder);
//        }
//    }
//
//    @Record(Record.Type.STATIC_INIT)
//    public void record(UndertowDeploymentTemplate template,
//                       @Template JaxrsTemplate template2,
//                       BuildProducer<DeploymentInfoBuildItem> producer) {
//
//        DeploymentInfo info = template.newDeploymentInfo();
//        producer.produce(new DeploymentInfoBuildItem(info));
//
//    }
//    @Record(MAIN)
//    public void recordMain(@Template UndertowDeploymentTemplate template,
//                       @Template JaxrsTemplate template2,
//                       BuildProducer<DeploymentInfoBuildItem> producer) {
//
//        DeploymentInfo info = template.newDeploymentInfo();
//        producer.produce(new DeploymentInfoBuildItem(info));
//
//    }

}
