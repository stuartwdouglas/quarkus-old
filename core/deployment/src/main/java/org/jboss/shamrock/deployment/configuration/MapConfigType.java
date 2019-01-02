package org.jboss.shamrock.deployment.configuration;

import java.util.Map;
import java.util.TreeMap;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.AssignableResultHandle;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;

/**
 */
public class MapConfigType extends CompoundConfigType {
    public MapConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment) {
        super(containingName, container, consumeSegment);
    }

    @SuppressWarnings("unchecked")
    Object getChildObject(final ConfigPropertyName name, final SmallRyeConfig config, final Object self, final String childName) {
        return ((TreeMap<String, Object>)self).get(childName);
    }

    ResultHandle generateGetChildObject(final BytecodeCreator body, final ResultHandle name, final ResultHandle config, final ResultHandle self, final String childName) {
        return body.invokeVirtualMethod(MethodDescriptor.ofMethod(Map.class, "get", Object.class), body.checkCast(self, Map.class), body.load(childName));
    }

    @SuppressWarnings("unchecked")
    void setChildObject(final Object self, final String childName, final Object value) {
        ((TreeMap<String, Object>)self).put(childName, value);
    }

    void generateSetChildObject(final BytecodeCreator body, final ResultHandle self, final String name, final ResultHandle value) {
        body.invokeVirtualMethod(MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class), body.checkCast(self, Map.class), body.load(name), value);
    }

    TreeMap<String, Object> getOrCreate(final ConfigPropertyName name, final SmallRyeConfig config) {
        final CompoundConfigType container = getContainer();
        if (container != null) {
            final Object enclosing = container.getOrCreate(name.getParent(), config);
            @SuppressWarnings("unchecked")
            TreeMap<String, Object> self = (TreeMap<String, Object>) container.getChildObject(name.getParent(), config, enclosing, getContainingName());
            if (self == null) {
                self = new TreeMap<>();
                container.setChildObject(enclosing, getContainingName(), self);
            }
            return self;
        } else {
            return new TreeMap<>();
        }
    }

    ResultHandle generateGetOrCreate(final BytecodeCreator body, final ResultHandle name, final ResultHandle config) {
        final CompoundConfigType container = getContainer();
        if (container != null) {
            if (isConsumeSegment()) body.invokeVirtualMethod(NI_PREV_METHOD, name);
            final ResultHandle enclosing = container.generateGetOrCreate(body, name, config);
            final AssignableResultHandle self = body.createVariable(TreeMap.class);
            body.assign(self, body.checkCast(container.generateGetChildObject(body, name, config, enclosing, getContainingName()), Map.class));
            final BytecodeCreator selfIsNull = body.ifNull(self).trueBranch();
            selfIsNull.assign(self, selfIsNull.newInstance(MethodDescriptor.ofConstructor(TreeMap.class)));
            container.generateSetChildObject(selfIsNull, self, getContainingName(), self);
            if (isConsumeSegment()) body.invokeVirtualMethod(NI_NEXT_METHOD, name);
            return self;
        } else {
            return body.newInstance(MethodDescriptor.ofConstructor(TreeMap.class));
        }
    }

    public boolean areChildrenRoots() {
        return false;
    }

    public String getClassName() {
        return Map.class.getName();
    }

    public ResultHandle writeInitialization(final BytecodeCreator body, final ResultHandle smallRyeConfig) {
        return body.newInstance(MethodDescriptor.ofConstructor(TreeMap.class));
    }

    void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config) {
        getContainer().setChildObject(enclosing, getContainingName(), new TreeMap<>());
    }

    void generateGetDefaultValueIntoEnclosing(final BytecodeCreator body, final ResultHandle name, final ResultHandle enclosing, final ResultHandle config) {
        getContainer().generateSetChildObject(body, enclosing, getContainingName(), body.newInstance(MethodDescriptor.ofConstructor(TreeMap.class)));
    }
}
