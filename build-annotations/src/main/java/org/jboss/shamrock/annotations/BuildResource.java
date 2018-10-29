package org.jboss.shamrock.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jboss.builder.item.BuildItem;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface BuildResource {

    Class<? extends BuildItem> type() default BuildItem.class;

    boolean optional() default false;

}
