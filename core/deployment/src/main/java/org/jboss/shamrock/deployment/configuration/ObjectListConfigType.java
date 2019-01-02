package org.jboss.shamrock.deployment.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;
import org.jboss.shamrock.runtime.configuration.ArrayListFactory;

/**
 */
public class ObjectListConfigType extends ObjectConfigType {
    public ObjectListConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String defaultValue, final String expectedTypeClassName, final ClassLoader classLoader) {
        super(containingName, container, consumeSegment, defaultValue, expectedTypeClassName, classLoader);
    }

    public ObjectListConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String defaultValue, final Class<?> expectedType) {
        super(containingName, container, consumeSegment, defaultValue, expectedType);
    }

    public void acceptConfigurationValue(final ConfigPropertyName name, final SmallRyeConfig config) {
        checkLoaded();
        final CompoundConfigType container = getContainer();
        final Object containerObj = container.getOrCreate(name.getParent(), config);
        container.setChildObject(containerObj, getContainingName(), config.getValues(name.toString(), expectedType, ArrayList::new));
    }

    public void generateAcceptConfigurationValue(final BytecodeCreator body, final ResultHandle name, final ResultHandle config) {
        final CompoundConfigType container = getContainer();
        if (isConsumeSegment()) body.invokeVirtualMethod(NI_PREV_METHOD, name);
        final ResultHandle containerObj = container.generateGetOrCreate(body, name, config);
        if (isConsumeSegment()) body.invokeVirtualMethod(NI_NEXT_METHOD, name);
        container.generateSetChildObject(body, containerObj, getContainingName(),
            body.invokeVirtualMethod(
                MethodDescriptor.ofMethod(SmallRyeConfig.class, "getValues", Collection.class, String.class, Class.class, IntFunction.class),
                config,
                name,
                body.loadClass(expectedTypeClassName),
                body.invokeStaticMethod(MethodDescriptor.ofMethod(ArrayListFactory.class, "getInstance", ArrayListFactory.class))
            )
        );
    }

    void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config) {
        checkLoaded();
        getContainer().setChildObject(enclosing, getContainingName(), new ArrayList<>());
    }

    void generateGetDefaultValueIntoEnclosing(final BytecodeCreator body, final ResultHandle name, final ResultHandle enclosing, final ResultHandle config) {
        getContainer().generateSetChildObject(body, enclosing, getContainingName(), body.newInstance(MethodDescriptor.ofConstructor(ArrayList.class)));
    }

    public ResultHandle writeInitialization(final BytecodeCreator body, final ResultHandle smallRyeConfig) {
        return body.checkCast(body.invokeVirtualMethod(SRC_CONVERT_METHOD, smallRyeConfig, body.load(defaultValue), body.loadClass(expectedTypeClassName)), List.class);
    }
}
