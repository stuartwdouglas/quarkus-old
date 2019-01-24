package org.jboss.shamrock.vertx.runtime;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shamrock.runtime.ConfigGroup;

@ConfigGroup
public class PemKeyCertConfiguration {

    /**
     * Comma-separated list of the path to the key files (Pem format).
     */
    @ConfigProperty(name = "keys")
    public Optional<String> keys;

    /**
     * Comma-separated list of the path to the certificate files (Pem format).
     */
    @ConfigProperty(name = "certs")
    public Optional<String> certs;

}
