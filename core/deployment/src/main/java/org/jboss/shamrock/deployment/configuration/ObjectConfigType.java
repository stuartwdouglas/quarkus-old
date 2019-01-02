package org.jboss.shamrock.deployment.configuration;

import java.util.Optional;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;

/**
 */
public class ObjectConfigType extends LeafConfigType {
    final String defaultValue;
    final String expectedTypeClassName;
    final ClassLoader classLoader;

    Class<?> expectedType;

    public ObjectConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String defaultValue, final String expectedTypeClassName, final ClassLoader classLoader) {
        super(containingName, container, consumeSegment);
        this.defaultValue = defaultValue;
        this.expectedTypeClassName = expectedTypeClassName;
        this.classLoader = classLoader;
    }

    public ObjectConfigType(final String name, final CompoundConfigType container, final boolean consumeSegment, final String defaultValue, final Class<?> expectedType) {
        this(name, container, consumeSegment, defaultValue, expectedType.getName(), expectedType.getClassLoader());
        this.expectedType = expectedType;
    }

    public void load() throws ClassNotFoundException {
        super.load();
        if (expectedType == null) {
            expectedType = Class.forName(expectedTypeClassName, true, classLoader);
        }
    }

    void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config) {
        checkLoaded();
        getContainer().setChildObject(enclosing, getContainingName(), config.convert(defaultValue, expectedType));
    }

    void generateGetDefaultValueIntoEnclosing(final BytecodeCreator body, final ResultHandle name, final ResultHandle enclosing, final ResultHandle config) {
        getContainer().generateSetChildObject(body, enclosing, getContainingName(), writeInitialization(body, config));
    }

    public ResultHandle writeInitialization(final BytecodeCreator body, final ResultHandle smallRyeConfig) {
        return body.checkCast(body.invokeVirtualMethod(SRC_CONVERT_METHOD, smallRyeConfig, body.load(defaultValue), body.loadClass(expectedTypeClassName)), expectedTypeClassName);
    }

    void checkLoaded() {
        if (expectedType == null) throw notLoadedException();
    }

    public void acceptConfigurationValue(final ConfigPropertyName name, final SmallRyeConfig config) {
        checkLoaded();
        final CompoundConfigType container = getContainer();
        final Object containerObj = container.getOrCreate(name.getParent(), config);
        container.setChildObject(containerObj, getContainingName(), getValue(name, config, expectedType));
    }

    public void generateAcceptConfigurationValue(final BytecodeCreator body, final ResultHandle name, final ResultHandle config) {
        final CompoundConfigType container = getContainer();
        if (isConsumeSegment()) body.invokeVirtualMethod(NI_PREV_METHOD, name);
        final ResultHandle containerObj = container.generateGetOrCreate(body, name, config);
        if (isConsumeSegment()) body.invokeVirtualMethod(NI_NEXT_METHOD, name);
        final ResultHandle optionalValue = body.invokeVirtualMethod(MethodDescriptor.ofMethod(SmallRyeConfig.class, "getOptionalValue", Object.class, String.class, Class.class), config, name, body.loadClass(expectedTypeClassName));
        body.invokeVirtualMethod(MethodDescriptor.ofMethod(Optional.class, "orElse", Object.class, Object.class), optionalValue, writeInitialization(body, config));
        container.generateSetChildObject(body, containerObj, getContainingName(), optionalValue);
    }

    private <T> T getValue(final ConfigPropertyName name, final SmallRyeConfig config, Class<T> expectedType) {
        return config.getOptionalValue(name.toString(), expectedType).orElse(config.convert(defaultValue, expectedType));
    }
}
