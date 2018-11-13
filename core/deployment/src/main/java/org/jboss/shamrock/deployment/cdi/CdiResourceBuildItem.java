package org.jboss.shamrock.deployment.cdi;

import org.jboss.builder.item.MultiBuildItem;

/**
 * a build item used to make sure all resource are initialized before CDI is started
 */
public final class CdiResourceBuildItem extends MultiBuildItem {
}
