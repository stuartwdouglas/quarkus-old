package org.jboss.shamrock.deployment.configuration;

import java.util.Optional;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;

/**
 */
public class OptionalObjectConfigType extends ObjectConfigType {
    public OptionalObjectConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String defaultValue, final String expectedTypeClassName, final ClassLoader classLoader) {
        super(containingName, container, consumeSegment, defaultValue, expectedTypeClassName, classLoader);
    }

    public OptionalObjectConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String defaultValue, final Class<?> expectedType) {
        super(containingName, container, consumeSegment, defaultValue, expectedType);
    }

    public void acceptConfigurationValue(final ConfigPropertyName name, final SmallRyeConfig config) {
        checkLoaded();
        final CompoundConfigType container = getContainer();
        final Object containerObj = container.getOrCreate(name.getParent(), config);
        container.setChildObject(containerObj, getContainingName(), config.getOptionalValue(name.toString(), expectedType));
    }

    public void generateAcceptConfigurationValue(final BytecodeCreator body, final ResultHandle name, final ResultHandle config) {
        final CompoundConfigType container = getContainer();
        if (isConsumeSegment()) body.invokeVirtualMethod(NI_PREV_METHOD, name);
        final ResultHandle containerObj = container.generateGetOrCreate(body, name, config);
        if (isConsumeSegment()) body.invokeVirtualMethod(NI_NEXT_METHOD, name);
        final ResultHandle optionalValue = body.invokeVirtualMethod(MethodDescriptor.ofMethod(SmallRyeConfig.class, "getOptionalValue", Object.class, String.class, Class.class), config, name, body.loadClass(expectedTypeClassName));
        container.generateSetChildObject(body, containerObj, getContainingName(), optionalValue);
    }

    void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config) {
        checkLoaded();
        getContainer().setChildObject(enclosing, getContainingName(), Optional.ofNullable(config.convert(defaultValue, expectedType)));
    }

    void generateGetDefaultValueIntoEnclosing(final BytecodeCreator body, final ResultHandle name, final ResultHandle enclosing, final ResultHandle config) {
        getContainer().generateSetChildObject(body, enclosing, getContainingName(), writeInitialization(body, config));
    }

    public ResultHandle writeInitialization(final BytecodeCreator body, final ResultHandle smallRyeConfig) {
        return body.checkCast(
            body.invokeStaticMethod(MethodDescriptor.ofMethod(Optional.class, "ofNullable", Optional.class, Object.class), super.writeInitialization(body, smallRyeConfig)),
            Optional.class
        );
    }
}

