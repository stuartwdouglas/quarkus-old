package org.jboss.shamrock.deployment.configuration;

import java.lang.reflect.Field;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.ResultHandle;
import org.wildfly.common.annotation.NotNull;

/**
 * A node which contains a regular value.  Leaf nodes can never be directly acquired.
 */
public abstract class LeafConfigType extends ConfigType {

    LeafConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment) {
        super(containingName, container, consumeSegment);
    }

    static Object getFromField(Field field, Object obj) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }

    /**
     * Handle a configuration key from the input file.
     *
     * @param name the configuration property name
     * @param config the source configuration
     */
    public abstract void acceptConfigurationValue(@NotNull ConfigPropertyName name, @NotNull SmallRyeConfig config);

    public abstract void generateAcceptConfigurationValue(final BytecodeCreator body, final ResultHandle name, final ResultHandle config);
}
