package org.jboss.shamrock.deployment;

import org.jboss.builder.item.MultiBuildItem;

public class RuntimeInitializedClass extends MultiBuildItem {

    private final String className;

    public RuntimeInitializedClass(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }
}
