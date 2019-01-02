package org.jboss.shamrock.deployment.configuration;

import java.util.Optional;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;

/**
 */
public class BooleanConfigType extends LeafConfigType {
    final String defaultValue;

    public BooleanConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String defaultValue) {
        super(containingName, container, consumeSegment);
        this.defaultValue = defaultValue;
    }

    public void acceptConfigurationValue(final ConfigPropertyName name, final SmallRyeConfig config) {
        final GroupConfigType container = getContainer(GroupConfigType.class);
        final Object containerObj = container.getOrCreate(isConsumeSegment() ? name.getParent() : name, config);
        container.setChildBoolean(containerObj, getContainingName(), config.getOptionalValue(name.toString(), Boolean.class).orElse(config.convert(defaultValue, Boolean.class)).booleanValue());
    }

    public void generateAcceptConfigurationValue(final BytecodeCreator body, final ResultHandle name, final ResultHandle config) {
        final GroupConfigType container = getContainer(GroupConfigType.class);
        final ResultHandle containerObj = container.generateGetOrCreate(body, isConsumeSegment() ? name/*.getParent() todo*/ : name, config);
        // config.getOptionalValue(name.toString(), Boolean.class).orElse(config.convert(defaultValue, Boolean.class)).booleanValue()
        final ResultHandle optionalValue = body.checkCast(body.invokeVirtualMethod(
            MethodDescriptor.ofMethod(SmallRyeConfig.class, "getOptionalValue", Optional.class, String.class, Object.class),
            config,
            body.invokeVirtualMethod(
                MethodDescriptor.ofMethod(Object.class, "toString", String.class),
                name
            ),
            body.loadClass(Boolean.class)
        ), Optional.class);
        final ResultHandle convertedDefault = getConvertedDefault(body, config);
        final ResultHandle defaultedValue = body.checkCast(body.invokeVirtualMethod(
            MethodDescriptor.ofMethod(Optional.class, "orElse", Object.class, Object.class),
            optionalValue,
            convertedDefault
        ), Boolean.class);
        final ResultHandle booleanValue = body.invokeVirtualMethod(MethodDescriptor.ofMethod(Boolean.class, "booleanValue", boolean.class), defaultedValue);
        container.generateSetChildBoolean(body, containerObj, getContainingName(), booleanValue);
    }

    void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config) {
        getContainer(GroupConfigType.class).setChildBoolean(enclosing, getContainingName(), config.convert(defaultValue, Boolean.class).booleanValue());
    }

    void generateGetDefaultValueIntoEnclosing(final BytecodeCreator body, final ResultHandle name, final ResultHandle enclosing, final ResultHandle config) {
        getContainer(GroupConfigType.class).generateSetChildBoolean(body, enclosing, getContainingName(), writeInitialization(body, config));
    }

    public ResultHandle writeInitialization(final BytecodeCreator body, final ResultHandle smallRyeConfig) {
        final MethodDescriptor unbox = MethodDescriptor.ofMethod(Boolean.class, "booleanValue", boolean.class);
        return body.invokeVirtualMethod(unbox, body.checkCast(getConvertedDefault(body, smallRyeConfig), Boolean.class));
    }

    private ResultHandle getConvertedDefault(final BytecodeCreator body, final ResultHandle config) {
        return body.checkCast(body.invokeVirtualMethod(
            SRC_CONVERT_METHOD,
            config,
            body.load(defaultValue),
            body.loadClass(Boolean.class)
        ), Boolean.class);
    }
}
