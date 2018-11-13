package org.jboss.shamrock.deployment.cdi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.jboss.builder.item.SimpleBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;

public final class BeanDeployment extends SimpleBuildItem {

    private final Map<String, byte[]> generatedBeans = new HashMap<>();

    // Lite profile
    private final List<BiFunction<AnnotationTarget, Collection<AnnotationInstance>, Collection<AnnotationInstance>>> annotationTransformers = new ArrayList<>();

    // Full profile
    private final List<String> extensions = new ArrayList<>();

    public void addGeneratedBean(String name, byte[] bean) {
        generatedBeans.put(name, bean);
    }

    public void addAnnotationTransformer(BiFunction<AnnotationTarget, Collection<AnnotationInstance>, Collection<AnnotationInstance>> transformer) {
        annotationTransformers.add(transformer);
    }

    public void addExtension(String extensionClass) {
        extensions.add(extensionClass);
    }

    public Map<String, byte[]> getGeneratedBeans() {
        return generatedBeans;
    }

    public List<BiFunction<AnnotationTarget, Collection<AnnotationInstance>, Collection<AnnotationInstance>>> getAnnotationTransformers() {
        return annotationTransformers;
    }

    public List<String> getExtensions() {
        return extensions;
    }

}
