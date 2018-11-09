package org.jboss.shamrock.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a given BuildStep is used to produce recorded bytecode.
 *
 * This method must inject one or more classes annotated with {@link Template},
 * that are used to record the resulting bytecode.
 *
 * The
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Record {

    Type value();

    enum Type {
        MAIN,
        STATIC_INIT
    }

}
