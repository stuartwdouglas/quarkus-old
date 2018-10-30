package org.jboss.shamrock.deployment.steps;

import java.util.Objects;

import org.jboss.builder.BuildContext;
import org.jboss.builder.BuildStep;
import org.jboss.shamrock.deployment.builditem.CapabilityBuildItem;

public class CapabilityBuildStep implements BuildStep {

    private final String capability;

    public CapabilityBuildStep(String capability) {
        Objects.nonNull(capability);
        this.capability = capability;
    }

    @Override
    public void execute(BuildContext context) {
        context.produce(new CapabilityBuildItem(capability));
    }
}
