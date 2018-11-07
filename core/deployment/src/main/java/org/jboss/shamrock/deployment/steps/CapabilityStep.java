package org.jboss.shamrock.deployment.steps;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import javax.inject.Inject;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.Capabilities;
import org.jboss.shamrock.deployment.builditem.CapabilityBuildItem;

@BuildStep
public class CapabilityStep implements BuildProcessingStep {

    @Inject
    List<CapabilityBuildItem> capabilitites;

    @Inject
    BuildProducer<Capabilities> producer;

    @Override
    public void build() throws Exception {
        Set<String> present = new HashSet<>();
        for (CapabilityBuildItem i : capabilitites) {
            present.add(i.getName());
        }

        producer.produce(new Capabilities(present));

    }
}
