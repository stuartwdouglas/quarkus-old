package org.jboss.shamrock.camel.component.salesforce.deployment;

import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.ProtocolHandlers;
import org.jboss.shamrock.deployment.annotations.BuildProducer;
import org.jboss.shamrock.deployment.annotations.BuildStep;
import org.jboss.shamrock.deployment.builditem.substrate.ReflectiveClassBuildItem;


class JettyProcessor {
    private static final List<Class<?>> JETTY_REFLECTIVE_CLASSES = Arrays.asList(
        HttpClient.class,
        ProtocolHandlers.class
    );

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @BuildStep
    void process() {
        JETTY_REFLECTIVE_CLASSES.stream()
            .map(Class::getName)
            .forEach(clazz -> addReflectiveClass(true, false, clazz));
    }

    private void addReflectiveClass(boolean methods, boolean fields, String... className) {
        reflectiveClass.produce(new ReflectiveClassBuildItem(methods, fields, className));
    }
}