package org.jboss.shamrock.camel.core.deployment;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

import org.apache.camel.Consumer;
import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.strategy.GenericFileProcessStrategyFactory;
import org.apache.camel.impl.converter.DoubleMap;
import org.apache.camel.spi.ExchangeFormatter;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.shamrock.camel.runtime.CamelRuntime;
import org.jboss.shamrock.camel.runtime.CamelTemplate;
import org.jboss.shamrock.camel.runtime.RuntimeRegistry;
import org.jboss.shamrock.deployment.annotations.BuildProducer;
import org.jboss.shamrock.deployment.annotations.BuildStep;
import org.jboss.shamrock.deployment.annotations.ExecutionTime;
import org.jboss.shamrock.deployment.annotations.Record;
import org.jboss.shamrock.deployment.builditem.ApplicationArchivesBuildItem;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.ShutdownContextBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.ReflectiveMethodBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.SubstrateConfigBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.SubstrateResourceBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.SubstrateResourceBundleBuildItem;
import org.jboss.shamrock.deployment.recording.RecorderContext;
import org.jboss.shamrock.runtime.RuntimeValue;


class CamelProcessor {
    private static final String CAMEL_SERVICE_BASE_PATH = "META-INF/services/org/apache/camel";

    private static final List<Class<?>> CAMEL_REFLECTIVE_CLASSES = Arrays.asList(
        Endpoint.class,
        Consumer.class,
        Producer.class,
        TypeConverter.class,
        ExchangeFormatter.class,
        GenericFileProcessStrategy.class
    );
    private static final List<Class<? extends Annotation>> CAMEL_REFLECTIVE_ANNOTATIONS = Arrays.asList(
        Converter.class
    );

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;
    @Inject
    BuildProducer<ReflectiveMethodBuildItem> reflectiveMethod;
    @Inject
    BuildProducer<SubstrateResourceBuildItem> resource;
    @Inject
    BuildProducer<SubstrateResourceBundleBuildItem> resourceBundle;
    @Inject
    ApplicationArchivesBuildItem applicationArchivesBuildItem;
    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @BuildStep
    SubstrateConfigBuildItem processSystemProperties() {
        return SubstrateConfigBuildItem.builder()
            .addNativeImageSystemProperty("CamelSimpleLRUCacheFactory", "true")
            .build();
    }

    @BuildStep(applicationArchiveMarkers = { CAMEL_SERVICE_BASE_PATH })
    void process() {
        IndexView view = combinedIndexBuildItem.getIndex();

        CAMEL_REFLECTIVE_CLASSES.stream()
            .map(Class::getName)
            .map(DotName::createSimple)
            .map(view::getAllKnownImplementors)
            .flatMap(Collection::stream)
            .filter(CamelSupport::isPublic)
            .forEach(v -> addReflectiveClass(true, true, v.name().toString()));

        CAMEL_REFLECTIVE_ANNOTATIONS.stream()
            .map(Class::getName)
            .map(DotName::createSimple)
            .map(view::getAnnotations)
            .flatMap(Collection::stream)
            .forEach(v -> {
                if (v.target().kind() == AnnotationTarget.Kind.CLASS) {
                    addReflectiveClass(true, false, v.target().asClass().name().toString());
                }
                if (v.target().kind() == AnnotationTarget.Kind.METHOD) {
                    addReflectiveMethod(v.target().asMethod());
                }
            }
        );

        addReflectiveClass(false, false, GenericFile.class.getName());
        addReflectiveClass(true, false, GenericFileProcessStrategyFactory.class.getName());

        processServices();
    }

    @BuildStep(applicationArchiveMarkers = { CAMEL_SERVICE_BASE_PATH })
    @Record(ExecutionTime.STATIC_INIT)
    CamelRuntimeBuildItem createInitiTask(RecorderContext recorderContext, CamelTemplate template) throws Exception {
        Properties properties = new Properties();
        Config config = ConfigProvider.getConfig();
        for(String i : config.getPropertyNames()) {
            properties.put(i, config.getValue(i, String.class));
        }
        String clazz = properties.getProperty(CamelRuntime.PROP_CAMEL_RUNTIME, CamelRuntime.class.getName());
        RuntimeValue<?> iruntime = recorderContext.newInstance(clazz);

        RuntimeRegistry registry = new RuntimeRegistry();
        processServices().forEach((n, c, o) -> registry.bind(n, c, recorderContext.newInstance(o)));

        List<RuntimeValue<?>> ibuilders = getInitRouteBuilderClasses()
            .map(recorderContext::newInstance)
            .collect(Collectors.toList());

        return new CamelRuntimeBuildItem(template.init(iruntime, registry, properties, ibuilders));
    }

    @BuildStep(applicationArchiveMarkers = { CAMEL_SERVICE_BASE_PATH })
    @Record(ExecutionTime.RUNTIME_INIT)
    void createRuntimeInitiTask(CamelTemplate template, CamelRuntimeBuildItem runtime, ShutdownContextBuildItem shutdown) throws Exception {
        template.start(shutdown, runtime.getRuntime());
    }

    protected Stream<String> getInitRouteBuilderClasses() {
        Set<ClassInfo> allKnownImplementors = new HashSet<>();
        allKnownImplementors.addAll(combinedIndexBuildItem.getIndex().getAllKnownImplementors(DotName.createSimple(RoutesBuilder.class.getName())));
        allKnownImplementors.addAll(combinedIndexBuildItem.getIndex().getAllKnownSubclasses(DotName.createSimple(RouteBuilder.class.getName())));
        allKnownImplementors.addAll(combinedIndexBuildItem.getIndex().getAllKnownSubclasses(DotName.createSimple(AdviceWithRouteBuilder.class.getName())));

        return allKnownImplementors
                .stream()
                .filter(CamelSupport::isConcrete)
                .filter(CamelSupport::isPublic)
                .map(ClassInfo::toString);
    }

    // Camel services files
    protected DoubleMap<String, Class<?>, String> processServices() {
        DoubleMap<String, Class<?>, String> map = new DoubleMap<>(256);
        iterateResources(CAMEL_SERVICE_BASE_PATH).forEach(p -> addCamelService(p, map));
        return map;
    }

    protected void addCamelService(Path p, DoubleMap<String, Class<?>, String> map) {
        String name = p.getFileName().toString();
        try (InputStream is = Files.newInputStream(p)) {
            Properties props = new Properties();
            props.load(is);
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String k = entry.getKey().toString();
                if (k.equals("class")) {
                    String clazz = entry.getValue().toString();
                    Class cl = Class.forName(clazz);
                    map.put(name, cl, clazz);
                    addReflectiveClass(true, false, clazz);
                } else if (k.endsWith(".class")) {
                    // Used for strategy.factory.class
                    String clazz = entry.getValue().toString();
                    addReflectiveClass(true, false, clazz);
                    addResource(p);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Stream<Path> iterateResources(String path) {
        return applicationArchivesBuildItem.getAllApplicationArchives().stream()
            .map(arch -> arch.getArchiveRoot().resolve(path))
            .filter(Files::isDirectory)
            .flatMap(CamelSupport::safeWalk)
            .filter(Files::isRegularFile);
    }

    protected void addResource(Path p) {
        addResource(p.toString().substring(1));
    }

    protected void addResource(String r) {
        resource.produce(new SubstrateResourceBuildItem(r));
    }

    protected void addReflectiveClass(boolean methods, boolean fields, String... className) {
        reflectiveClass.produce(new ReflectiveClassBuildItem(methods, false, className));
    }

    protected void addReflectiveMethod(MethodInfo mi) {
        reflectiveMethod.produce(new ReflectiveMethodBuildItem(mi));
    }

    protected void addResourceBundle(String bundle) {
        resourceBundle.produce(new SubstrateResourceBundleBuildItem(bundle));
    }
}
