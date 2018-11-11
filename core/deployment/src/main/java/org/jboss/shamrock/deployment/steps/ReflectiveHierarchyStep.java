package org.jboss.shamrock.deployment.steps;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.jandex.UnresolvedTypeVariable;
import org.jboss.jandex.VoidType;
import org.jboss.logging.Logger;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import javax.inject.Inject;

import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveHierarchyBuildItem;

public class ReflectiveHierarchyStep {

    private static final Logger log = Logger.getLogger(ReflectiveHierarchyStep.class);

    @Inject
    List<ReflectiveHierarchyBuildItem> heiracy;

    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @BuildStep
    public void build() throws Exception {
        Set<DotName> processedReflectiveHierarchies = new HashSet<>();
        for (ReflectiveHierarchyBuildItem i : heiracy) {
            addReflectiveHierarchy(i.getType(), processedReflectiveHierarchies);
        }
    }

    private void addReflectiveHierarchy(Type type, Set<DotName> processedReflectiveHierarchies) {

        if (type instanceof VoidType ||
                type instanceof PrimitiveType ||
                type instanceof UnresolvedTypeVariable) {
            return;
        } else if (type instanceof ClassType) {
            addClassTypeHierarchy(type.name(), processedReflectiveHierarchies);
        } else if (type instanceof ArrayType) {
            addReflectiveHierarchy(type.asArrayType().component(), processedReflectiveHierarchies);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) type;
            addReflectiveHierarchy(p.owner(), processedReflectiveHierarchies);
            for (Type arg : p.arguments()) {
                addReflectiveHierarchy(arg, processedReflectiveHierarchies);
            }
        }
    }

    private void addClassTypeHierarchy(DotName name, Set<DotName> processedReflectiveHierarchies) {
        if (name.toString().startsWith("java.") ||
                processedReflectiveHierarchies.contains(name)) {
            return;
        }
        processedReflectiveHierarchies.add(name);
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, name.toString()));
        ClassInfo info = combinedIndexBuildItem.getIndex().getClassByName(name);
        if (info == null) {
            log.warn("Unable to find annotation info for " + name + ", it may not be correctly registered for reflection");
        } else {
            addClassTypeHierarchy(info.superName(), processedReflectiveHierarchies);
            for (FieldInfo i : info.fields()) {
                addReflectiveHierarchy(i.type(), processedReflectiveHierarchies);
            }
            for (MethodInfo i : info.methods()) {
                addReflectiveHierarchy(i.returnType(), processedReflectiveHierarchies);
                for (Type p : i.parameters()) {
                    addReflectiveHierarchy(p, processedReflectiveHierarchies);
                }
            }
        }

    }

}
