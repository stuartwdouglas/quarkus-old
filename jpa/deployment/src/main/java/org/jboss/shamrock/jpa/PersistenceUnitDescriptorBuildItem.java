package org.jboss.shamrock.jpa;

import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;
import org.jboss.builder.item.MultiBuildItem;

public final class PersistenceUnitDescriptorBuildItem extends MultiBuildItem {

    private final PersistenceUnitDescriptor descriptor;

    public PersistenceUnitDescriptorBuildItem(PersistenceUnitDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public PersistenceUnitDescriptor getDescriptor() {
        return descriptor;
    }
}
