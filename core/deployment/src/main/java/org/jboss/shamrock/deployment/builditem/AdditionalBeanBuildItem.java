package org.jboss.shamrock.deployment.builditem;

import org.jboss.builder.item.MultiBuildItem;

public final class AdditionalBeanBuildItem extends MultiBuildItem {

    private final String beanName;

    public AdditionalBeanBuildItem(String beanName) {
        this.beanName = beanName;
    }

    public String getBeanName() {
        return beanName;
    }
}
