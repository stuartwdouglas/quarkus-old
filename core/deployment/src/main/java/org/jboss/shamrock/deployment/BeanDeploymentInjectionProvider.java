package org.jboss.shamrock.deployment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jboss.shamrock.deployment.builditem.BeanArchiveIndexBuildItem;
import org.jboss.shamrock.deployment.cdi.BeanDeployment;

public class BeanDeploymentInjectionProvider implements InjectionProvider {

    private final BeanDeployment deployment = new BeanDeployment();

    @Override
    public Set<Class<?>> getProvidedTypes() {
        return new HashSet<>(Arrays.asList(BeanDeployment.class, BeanArchiveIndexBuildItem.class));
    }

    @Override
    public <T> T getInstance(Class<T> type) {
        if (type == BeanDeployment.class) {
            return (T) deployment;
        }
        throw new IllegalArgumentException("invalid type");
    }
}
