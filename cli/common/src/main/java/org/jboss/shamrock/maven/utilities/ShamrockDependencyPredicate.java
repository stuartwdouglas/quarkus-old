package org.jboss.shamrock.maven.utilities;

import java.util.function.Predicate;

import org.apache.maven.model.Dependency;

public class ShamrockDependencyPredicate implements Predicate<Dependency> {
    @Override
    public boolean test(final Dependency d) {
        return d.getGroupId().equalsIgnoreCase(MojoUtils.SHAMROCK_GROUP_ID);
    }
}
