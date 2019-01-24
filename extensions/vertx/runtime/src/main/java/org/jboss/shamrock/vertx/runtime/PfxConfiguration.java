package org.jboss.shamrock.vertx.runtime;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shamrock.runtime.ConfigGroup;

@ConfigGroup
public class PfxConfiguration {

    /**
     * Path to the key file (PFX format)
     */
    @ConfigProperty(name = "path")
    public Optional<String> path;

    /**
     * Password of the key.
     */
    @ConfigProperty(name = "password")
    public Optional<String> password;
}
