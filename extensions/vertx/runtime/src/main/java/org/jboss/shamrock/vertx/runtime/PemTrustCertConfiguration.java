package org.jboss.shamrock.vertx.runtime;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shamrock.runtime.ConfigGroup;

@ConfigGroup
public class PemTrustCertConfiguration {

    /**
     * Comma-separated list of the trust certificate files (Pem format).
     */
    @ConfigProperty(name = "certs")
    public Optional<String> certs;

}
