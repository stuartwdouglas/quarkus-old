package org.jboss.shamrock.deployment;

import java.io.File;

import org.jboss.shamrock.deployment.annotations.BuildStep;
import org.jboss.shamrock.deployment.builditem.SystemPropertyBuildItem;
import org.jboss.shamrock.runtime.annotations.ConfigItem;
import org.jboss.shamrock.runtime.configuration.ConfigBoolean;

public class SslConfig {
    /**
     * Enable support for SSL on the resulting native binary
     */
    @ConfigItem(name = "ssl.native")
    ConfigBoolean enableSsl;
    
    @BuildStep
    SystemPropertyBuildItem setupNativeSsl() {
        String graalVmHome = System.getenv("GRAALVM_HOME");
        if(enableSsl != null && enableSsl.value) {
            // I assume we only fail if we actually enable it, but perhaps there's a no-native called that we can't
            // see here?
            
            // FIXME: fail build? what sort of error here?
            if(graalVmHome == null)
                throw new RuntimeException("GRAALVM_HOME environment variable required");
            return new SystemPropertyBuildItem("java.library.path", graalVmHome+File.separator+"jre"+File.separator+"lib"+File.separator+"amd64");
        }
        return null;
    }
}
