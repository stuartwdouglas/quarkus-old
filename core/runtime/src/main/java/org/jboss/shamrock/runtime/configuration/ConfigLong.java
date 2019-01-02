package org.jboss.shamrock.runtime.configuration;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shamrock.runtime.annotations.ConfigGroup;

@ConfigGroup
public class ConfigLong {
    @ConfigProperty(name = "")
    public long value;
}
