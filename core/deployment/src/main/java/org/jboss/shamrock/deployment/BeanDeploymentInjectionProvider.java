package org.jboss.shamrock.deployment;

import java.util.Collections;
import java.util.Set;

public class BeanDeploymentInjectionProvider implements InjectionProvider {

    private final BeanDeployment deployment = new BeanDeployment();

    @Override
    public Set<Class<?>> getProvidedTypes() {
        return Collections.singleton(BeanDeployment.class);
    }

    @Override
    public <T> T getInstance(Class<T> type) {
        if(type == BeanDeployment.class) {
            return (T) deployment;
        }
        throw new IllegalArgumentException("invalid type");
    }
}
