package org.jboss.shamrock.weld.deployment;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.BeanDeployment;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.Capabilities;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.BeanArchiveIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.BytecodeOutputBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.weld.runtime.WeldDeploymentTemplate;

import io.smallrye.config.inject.ConfigProducer;

@BuildStep(providesCapabilities = Capabilities.CDI_WELD, applicationArchiveMarkers = {"META-INF/beans.xml", "META-INF/services/javax.enterprise.inject.spi.Extension"})
public class WeldAnnotationProcessor implements BuildProcessingStep {

    @BuildResource
    BeanDeployment beanDeployment;

    @BuildResource
    BeanArchiveIndexBuildItem beanArchiveIndex;

    @BuildResource
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @BuildResource
    List<AdditionalBeanBuildItem> additionalBeans;

    @BuildResource
    BytecodeOutputBuildItem bytecode;

    @Override
    public void build() throws Exception {
        IndexView index = beanArchiveIndex.getIndex();
        List<String> additionalBeans = new ArrayList<>();
        for (AdditionalBeanBuildItem i : this.additionalBeans) {
            additionalBeans.addAll(i.getBeanNames());
        }
        //make config injectable
        additionalBeans.add(ConfigProducer.class.getName());
        try (BytecodeRecorder recorder = bytecode.addStaticInitTask(RuntimePriority.WELD_DEPLOYMENT)) {
            WeldDeploymentTemplate template = recorder.getRecordingProxy(WeldDeploymentTemplate.class);
            SeContainerInitializer init = template.createWeld();
            for (ClassInfo cl : index.getKnownClasses()) {
                String name = cl.name().toString();
                template.addClass(init, recorder.classProxy(name));
                reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, name));
            }
            for (String clazz : additionalBeans) {
                template.addClass(init, recorder.classProxy(clazz));
            }
            for (String clazz : beanDeployment.getGeneratedBeans().keySet()) {
                template.addClass(init, recorder.classProxy(clazz));
            }
            for (String extensionClazz : beanDeployment.getExtensions()) {
                template.addExtension(init, recorder.classProxy(extensionClazz));
            }
            SeContainer weld = template.doBoot(null, init);
            template.initBeanContainer(weld);
            template.setupInjection(null, weld);
        }

    }
}
