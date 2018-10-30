package org.jboss.shamrock.weld.deployment;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.shamrock.annotations.BuildProcessor;
import org.jboss.shamrock.deployment.ArchiveContext;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.builditem.BeanArchiveIndexBuildItem;
import org.jboss.shamrock.deployment.BeanDeployment;
import org.jboss.shamrock.deployment.ProcessorContext;
import org.jboss.shamrock.deployment.ResourceProcessor;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.weld.runtime.WeldDeploymentTemplate;

import io.smallrye.config.inject.ConfigProducer;

@BuildProcessor
public class WeldAnnotationProcessor implements BuildProcessingStep {

    @Inject
    private BeanDeployment beanDeployment;

    @Inject
    private BeanArchiveIndexBuildItem beanArchiveIndex;

    @Override
    public void build throws Exception {
        IndexView index = beanArchiveIndex.getIndex();
        //make config injectable
    	beanDeployment.addAdditionalBean(ConfigProducer.class);
        try (BytecodeRecorder recorder = processorContext.addStaticInitTask(RuntimePriority.WELD_DEPLOYMENT)) {
            WeldDeploymentTemplate template = recorder.getRecordingProxy(WeldDeploymentTemplate.class);
            SeContainerInitializer init = template.createWeld();
            for (ClassInfo cl : index.getKnownClasses()) {
                String name = cl.name().toString();
                template.addClass(init, recorder.classProxy(name));
                reflectiveClass.produce(new ReflectiveClassBuildItem((true, true, name);
            }
            for (String clazz : beanDeployment.getAdditionalBeans()) {
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

    @Override
    public int getPriority() {
        return RuntimePriority.WELD_DEPLOYMENT;
    }
}
