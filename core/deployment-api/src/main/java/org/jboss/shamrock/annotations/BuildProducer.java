package org.jboss.shamrock.annotations;

import org.jboss.builder.item.BuildItem;

public interface BuildProducer<T extends BuildItem> {

    void produce(T item);

}
