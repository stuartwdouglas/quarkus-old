package org.jboss.shamrock.deployment;

import org.jboss.builder.item.MultiBuildItem;

public class ReflectiveClass extends MultiBuildItem{

    private final String className;
    private final boolean methods;
    private final boolean fields;

    public ReflectiveClass(String className, boolean methods, boolean fields) {
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
