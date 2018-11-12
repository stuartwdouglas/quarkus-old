package org.jboss.shamrock.logging.deployment;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.Record;
import org.jboss.shamrock.deployment.builditem.LogSetupBuildItem;
import org.jboss.shamrock.logging.runtime.LogSetupTemplate;

public class SetupLoggingBuildStep {

    @Record(staticInit = true)
    @BuildStep
    public LogSetupBuildItem setup(LogSetupTemplate template) {
        template.initializeLogManager();
        return new LogSetupBuildItem();
    }
}
