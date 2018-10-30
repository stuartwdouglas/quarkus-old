package org.jboss.shamrock.deployment.builditem;

import org.jboss.builder.item.MultiBuildItem;

public final class CapabilityBuildItem extends MultiBuildItem {

    private final String name;

    public CapabilityBuildItem(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
