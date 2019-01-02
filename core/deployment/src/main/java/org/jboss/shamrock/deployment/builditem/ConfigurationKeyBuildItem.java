package org.jboss.shamrock.deployment.builditem;

import org.jboss.builder.item.MultiBuildItem;

/**
 *
 */
public final class ConfigurationKeyBuildItem extends MultiBuildItem {
    private final String baseAddress;

    public ConfigurationKeyBuildItem(final String baseAddress) {
        this.baseAddress = baseAddress;
    }

    public String getBaseAddress() {
        return baseAddress;
    }
}
