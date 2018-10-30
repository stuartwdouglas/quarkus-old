package org.jboss.shamrock.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jboss.builder.item.BuildItem;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface BuildProcessor {

    Class<? extends BuildItem>[] beforeConsume() default {};

    Class<? extends BuildItem>[] afterProduce() default {};

    String[] providesCapabilities() default {};
}
