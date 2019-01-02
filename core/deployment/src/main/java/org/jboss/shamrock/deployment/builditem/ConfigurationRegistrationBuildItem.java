package org.jboss.shamrock.deployment.builditem;

import java.lang.reflect.Type;

import org.jboss.builder.item.MultiBuildItem;

/**
 */
public final class ConfigurationRegistrationBuildItem extends MultiBuildItem {
    private final Type type;
    private final String baseKey;

    public ConfigurationRegistrationBuildItem(final Type type, final String baseKey) {
        this.type = type;
        this.baseKey = baseKey;
    }

    public Type getType() {
        return type;
    }

    public String getBaseKey() {
        return baseKey;
    }
}
