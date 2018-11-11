package org.jboss.shamrock.faulttolerance.deployment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import javax.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.deployment.BeanDeployment;
import org.jboss.shamrock.deployment.Capabilities;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.NativeImageSystemPropertyBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.faulttolerance.runtime.ShamrockFallbackHandlerProvider;
import org.jboss.shamrock.faulttolerance.runtime.ShamrockFaultToleranceOperationProvider;

import com.netflix.hystrix.HystrixCircuitBreaker;
import io.smallrye.faulttolerance.DefaultFallbackHandlerProvider;
import io.smallrye.faulttolerance.DefaultFaultToleranceOperationProvider;
import io.smallrye.faulttolerance.DefaultHystrixConcurrencyStrategy;
import io.smallrye.faulttolerance.HystrixCommandBinding;
import io.smallrye.faulttolerance.HystrixCommandInterceptor;
import io.smallrye.faulttolerance.HystrixExtension;
import io.smallrye.faulttolerance.HystrixInitializer;

public class FaultToleranceAnnotationProcessor {

    private static final DotName[] FT_ANNOTATIONS = {DotName.createSimple(Asynchronous.class.getName()), DotName.createSimple(Bulkhead.class.getName()),
            DotName.createSimple(CircuitBreaker.class.getName()), DotName.createSimple(Fallback.class.getName()), DotName.createSimple(Retry.class.getName()),
            DotName.createSimple(Timeout.class.getName())};

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    BuildProducer<NativeImageSystemPropertyBuildItem> nativeImageSystemProperty;

    @Inject
    BuildProducer<AdditionalBeanBuildItem> additionalBean;

    @Inject
    BeanDeployment beanDeployment;

    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @Inject
    Capabilities capabilities;

    @BuildStep
    public void build() throws Exception {

        IndexView index = combinedIndexBuildItem.getIndex();

        // Make sure rx.internal.util.unsafe.UnsafeAccess.DISABLED_BY_USER is set.
        nativeImageSystemProperty.produce(new NativeImageSystemPropertyBuildItem("rx.unsafe-disable", "true"));

        // Add reflective acccess to fallback handlers
        Collection<ClassInfo> fallbackHandlers = index.getAllKnownImplementors(DotName.createSimple(FallbackHandler.class.getName()));
        for (ClassInfo fallbackHandler : fallbackHandlers) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, false, fallbackHandler.name().toString()));
        }
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, true, HystrixCircuitBreaker.Factory.class.getName()));

        if (capabilities.isCapabilityPresent(Capabilities.CDI_ARC)) {
            // Add HystrixCommandBinding to app classes
            Set<String> ftClasses = new HashSet<>();
            for (DotName annotation : FT_ANNOTATIONS) {
                Collection<AnnotationInstance> annotationInstances = index.getAnnotations(annotation);
                for (AnnotationInstance instance : annotationInstances) {
                    if (instance.target().kind() == Kind.CLASS) {
                        ftClasses.add(instance.target().asClass().toString());
                    } else if (instance.target().kind() == Kind.METHOD) {
                        ftClasses.add(instance.target().asMethod().declaringClass().toString());
                    }
                }
            }
            if (!ftClasses.isEmpty()) {
                beanDeployment.addAnnotationTransformer(new BiFunction<AnnotationTarget, Collection<AnnotationInstance>, Collection<AnnotationInstance>>() {
                    @Override
                    public Collection<AnnotationInstance> apply(AnnotationTarget target, Collection<AnnotationInstance> annotations) {
                        if (Kind.CLASS != target.kind() || !ftClasses.contains(target.asClass().name().toString())) {
                            return annotations;
                        }
                        // Add @HystrixCommandBinding
                        List<AnnotationInstance> modified = new ArrayList<>(annotations);
                        modified.add(AnnotationInstance.create(DotName.createSimple(HystrixCommandBinding.class.getName()), target, new AnnotationValue[0]));
                        return modified;
                    }
                });
            }
            // Register bean classes
            additionalBean.produce(new AdditionalBeanBuildItem(
                    HystrixCommandInterceptor.class,
                    HystrixInitializer.class,
                    DefaultHystrixConcurrencyStrategy.class,
                    ShamrockFaultToleranceOperationProvider.class,
                    ShamrockFallbackHandlerProvider.class));
        } else {
            // Full CDI - add extension and reflective info
            beanDeployment.addExtension(HystrixExtension.class.getName());
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true,
                    HystrixCommandInterceptor.class.getName(),
                    HystrixInitializer.class.getName(),
                    DefaultHystrixConcurrencyStrategy.class.getName(),
                    DefaultFaultToleranceOperationProvider.class.getName(),
                    DefaultFallbackHandlerProvider.class.getName()));

            for (DotName annotation : FT_ANNOTATIONS) {
                // Needed for substrate VM
                reflectiveClass.produce(new ReflectiveClassBuildItem(true, false, annotation.toString()));
            }
        }
    }

}
