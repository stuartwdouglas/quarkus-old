package org.jboss.shamrock.deployment.cdi;

import java.util.function.Consumer;

import org.jboss.builder.item.MultiBuildItem;
import org.jboss.shamrock.runtime.BeanContainer;

public final class BeanConfiguratorBuildItem extends MultiBuildItem {

    private final Consumer<BeanContainer> beanConfigurator;

    public BeanConfiguratorBuildItem(Consumer<BeanContainer> beanConfigurator) {
        this.beanConfigurator = beanConfigurator;
    }

    public Consumer<BeanContainer> getBeanConfigurator() {
        return beanConfigurator;
    }
}
