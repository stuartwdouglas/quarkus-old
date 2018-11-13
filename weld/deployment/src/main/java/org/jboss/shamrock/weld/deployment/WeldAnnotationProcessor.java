package org.jboss.shamrock.weld.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.Record;
import org.jboss.shamrock.deployment.cdi.BeanConfiguratorBuildItem;
import org.jboss.shamrock.deployment.cdi.BeanDeployment;
import org.jboss.shamrock.deployment.Capabilities;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.BeanArchiveIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.BeanContainerBuildItem;
import org.jboss.shamrock.deployment.builditem.InjectionProviderBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.recording.BytecodeRecorder;
import org.jboss.shamrock.runtime.BeanContainer;
import org.jboss.shamrock.weld.runtime.WeldDeploymentTemplate;

import io.smallrye.config.inject.ConfigProducer;

public class WeldAnnotationProcessor {

    @Inject
    BeanDeployment beanDeployment;

    @Inject
    BeanArchiveIndexBuildItem beanArchiveIndex;

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    List<AdditionalBeanBuildItem> additionalBeans;

    @Record(staticInit = true)
    @BuildStep(providesCapabilities = Capabilities.CDI_WELD, applicationArchiveMarkers = {"META-INF/beans.xml", "META-INF/services/javax.enterprise.inject.spi.Extension"})
    public BeanContainerBuildItem build(WeldDeploymentTemplate template, BytecodeRecorder recorder, BuildProducer<InjectionProviderBuildItem> injectionProvider, List<BeanConfiguratorBuildItem> beanConfig) throws Exception {
        IndexView index = beanArchiveIndex.getIndex();
        List<String> additionalBeans = new ArrayList<>();
        for (AdditionalBeanBuildItem i : this.additionalBeans) {
            additionalBeans.addAll(i.getBeanNames());
        }
        //make config injectable
        additionalBeans.add(ConfigProducer.class.getName());
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
        BeanContainer container = template.initBeanContainer(weld, beanConfig.stream().map(BeanConfiguratorBuildItem::getBeanConfigurator).collect(Collectors.toList()));
        injectionProvider.produce(new InjectionProviderBuildItem());
        template.setupInjection(null, weld);
        return new BeanContainerBuildItem(container);
    }
}
