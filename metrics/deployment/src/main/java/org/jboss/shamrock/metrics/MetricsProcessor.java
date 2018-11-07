package org.jboss.shamrock.metrics;

import java.util.Collection;

import javax.interceptor.Interceptor;

import org.eclipse.microprofile.metrics.annotation.Counted;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.ShamrockConfig;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.BeanArchiveIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.BytecodeOutputBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.metrics.runtime.MetricsDeploymentTemplate;
import org.jboss.shamrock.metrics.runtime.MetricsServlet;
import org.jboss.shamrock.undertow.ServletData;

import io.smallrye.metrics.MetricProducer;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.MetricsRequestHandler;
import io.smallrye.metrics.interceptors.CountedInterceptor;
import io.smallrye.metrics.interceptors.MeteredInterceptor;
import io.smallrye.metrics.interceptors.MetricNameFactory;
import io.smallrye.metrics.interceptors.MetricsBinding;
import io.smallrye.metrics.interceptors.MetricsInterceptor;
import io.smallrye.metrics.interceptors.TimedInterceptor;

@BuildStep
public class MetricsProcessor implements BuildProcessingStep {

    @BuildResource
    ShamrockConfig config;

    @BuildResource
    BeanArchiveIndexBuildItem beanArchiveIndex;

    @BuildResource
    BuildProducer<ReflectiveClassBuildItem> reflectiveClasses;

    @BuildResource
    BuildProducer<ServletData> servlets;

    @BuildResource
    BytecodeOutputBuildItem bytecode;

    @BuildResource
    BuildProducer<AdditionalBeanBuildItem> additionalBeans;

    @Override
    public void build() throws Exception {
        ServletData servletData = new ServletData("metrics", MetricsServlet.class.getName());
        servletData.getMapings().add(config.getConfig("metrics.path", "/metrics"));
        servlets.produce(servletData);

        additionalBeans.produce(new AdditionalBeanBuildItem(MetricProducer.class,
                MetricNameFactory.class,
                MetricRegistries.class,
                MetricsInterceptor.class,
                MeteredInterceptor.class,
                CountedInterceptor.class,
                TimedInterceptor.class,
                MetricsRequestHandler.class,
                MetricsServlet.class));

        reflectiveClasses.produce(new ReflectiveClassBuildItem(false, false, Counted.class.getName()));
        reflectiveClasses.produce(new ReflectiveClassBuildItem(false, false, MetricsBinding.class.getName()));


        try (BytecodeRecorder recorder = bytecode.addStaticInitTask(RuntimePriority.WELD_DEPLOYMENT + 30)) {
            MetricsDeploymentTemplate metrics = recorder.getRecordingProxy(MetricsDeploymentTemplate.class);

            metrics.createRegistries(null);

            IndexView index = beanArchiveIndex.getIndex();
            Collection<AnnotationInstance> annos = index.getAnnotations(DotName.createSimple(Counted.class.getName()));

            for (AnnotationInstance anno : annos) {
                AnnotationTarget target = anno.target();

                // We need to exclude metrics interceptors
                if (Kind.CLASS.equals(target.kind())
                        && target.asClass().classAnnotations().stream().anyMatch(a -> a.name().equals(DotName.createSimple(Interceptor.class.getName())))) {
                    continue;
                }

                MethodInfo methodInfo = target.asMethod();
                String name = methodInfo.name();
                if (anno.value("name") != null) {
                    name = anno.value("name").asString();
                }
                ClassInfo classInfo = methodInfo.declaringClass();

                metrics.registerCounted(classInfo.name().toString(),
                        name);
            }

        }
        try (BytecodeRecorder recorder = bytecode.addDeploymentTask(RuntimePriority.WELD_DEPLOYMENT + 30)) {
            MetricsDeploymentTemplate metrics = recorder.getRecordingProxy(MetricsDeploymentTemplate.class);
            metrics.registerBaseMetrics();
            metrics.registerVendorMetrics();
        }
    }

}
