package org.jboss.shamrock.deployment.builditem;

import org.jboss.builder.item.MultiBuildItem;

public final class ReflectiveClassBuildItem extends MultiBuildItem{

    private final String className;
    private final boolean methods;
    private final boolean fields;

    public ReflectiveClassBuildItem(String className, boolean methods, boolean fields) {
        this.className = className;
        this.methods = methods;
        this.fields = fields;
    }
    public ReflectiveClassBuildItem(boolean methods, boolean fields, String className) {
        this.className = className;
        this.methods = methods;
        this.fields = fields;
    }

    public String getClassName() {
        return className;
    }

    public boolean isMethods() {
        return methods;
    }

    public boolean isFields() {
        return fields;
    }
}
