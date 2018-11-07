package org.jboss.shamrock.deployment;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import javax.inject.Inject;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.builditem.RuntimeInitializedClassBuildItem;
import org.jboss.shamrock.runtime.RegisterForReflection;

@BuildStep
public class RegisterForReflectionProcessor implements BuildProcessingStep {

    @Inject
    BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit;

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @Override
    public void build() throws Exception {
        for (AnnotationInstance i : combinedIndexBuildItem.getIndex().getAnnotations(DotName.createSimple(RegisterForReflection.class.getName()))) {
            ClassInfo target = i.target().asClass();
            boolean methods = i.value("methods") == null || i.value("methods").asBoolean();
            boolean fields = i.value("fields") == null || i.value("fields").asBoolean();
            reflectiveClass.produce(new ReflectiveClassBuildItem(methods, fields, target.name().toString()));
        }

        //TODO: where should stuff like this go?
        //this will hold a heap of memory when it is initialized, and is rarely used (but it generally on the analysis path)
        runtimeInit.produce(new RuntimeInitializedClassBuildItem("com.sun.org.apache.xml.internal.serializer.ToHTMLStream"));
    }

}
