package org.jboss.shamrock.deployment.configuration;

import java.util.OptionalInt;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;
import org.jboss.shamrock.runtime.configuration.NameIterator;

/**
 */
public class IntConfigType extends LeafConfigType {
    final String defaultValue;

    public IntConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String defaultValue) {
        super(containingName, container, consumeSegment);
        this.defaultValue = defaultValue;
    }

    public void acceptConfigurationValue(final ConfigPropertyName name, final SmallRyeConfig config) {
        final GroupConfigType container = getContainer(GroupConfigType.class);
        final Object containerObj = container.getOrCreate(isConsumeSegment() ? name.getParent() : name, config);
        container.setChildInt(containerObj, getContainingName(), config.getValue(name.toString(), OptionalInt.class).orElse(config.convert(defaultValue, Integer.class).intValue()));
    }

    public void generateAcceptConfigurationValue(final BytecodeCreator body, final ResultHandle name, final ResultHandle config) {
        final GroupConfigType container = getContainer(GroupConfigType.class);
        if (isConsumeSegment()) body.invokeVirtualMethod(MethodDescriptor.ofMethod(NameIterator.class, "previous", void.class), name);
        final ResultHandle containerObj = container.generateGetOrCreate(body, name, config);
        if (isConsumeSegment()) body.invokeVirtualMethod(MethodDescriptor.ofMethod(NameIterator.class, "next", void.class), name);
        // config.getValue(name.toString(), OptionalInt.class).orElse(config.convert(defaultValue, Integer.class).intValue())
        final ResultHandle optionalValue = body.checkCast(body.invokeVirtualMethod(
            MethodDescriptor.ofMethod(SmallRyeConfig.class, "getValue", Object.class, String.class, Object.class),
            config,
            body.invokeVirtualMethod(
                MethodDescriptor.ofMethod(Object.class, "toString", String.class),
                name
            ),
            body.loadClass(OptionalInt.class)
        ), OptionalInt.class);
        final ResultHandle convertedDefault = getConvertedDefault(body, config);
        final ResultHandle defaultedValue = body.checkCast(body.invokeVirtualMethod(
            MethodDescriptor.ofMethod(OptionalInt.class, "orElse", int.class, int.class),
            optionalValue,
            convertedDefault
        ), Integer.class);
        final ResultHandle intValue = body.invokeVirtualMethod(MethodDescriptor.ofMethod(Integer.class, "intValue", int.class), defaultedValue);
        container.generateSetChildInt(body, containerObj, getContainingName(), intValue);
    }

    void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config) {
        getContainer(GroupConfigType.class).setChildInt(enclosing, getContainingName(), config.convert(defaultValue, Integer.class).intValue());
    }

    void generateGetDefaultValueIntoEnclosing(final BytecodeCreator body, final ResultHandle name, final ResultHandle enclosing, final ResultHandle config) {
        getContainer(GroupConfigType.class).generateSetChildInt(body, enclosing, getContainingName(), writeInitialization(body, config));
    }

    public ResultHandle writeInitialization(final BytecodeCreator body, final ResultHandle smallRyeConfig) {
        final MethodDescriptor unbox = MethodDescriptor.ofMethod(Integer.class, "intValue", int.class);
        return body.invokeVirtualMethod(unbox, getConvertedDefault(body, smallRyeConfig));
    }

    private ResultHandle getConvertedDefault(final BytecodeCreator body, final ResultHandle config) {
        return body.checkCast(body.invokeVirtualMethod(
            SRC_CONVERT_METHOD,
            config,
            body.load(defaultValue),
            body.loadClass(Integer.class)
        ), Integer.class);
    }
}
