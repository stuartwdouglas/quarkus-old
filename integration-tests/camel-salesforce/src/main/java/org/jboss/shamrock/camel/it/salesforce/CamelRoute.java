package org.jboss.shamrock.camel.it.salesforce;

import org.apache.camel.builder.RouteBuilder;
import org.jboss.shamrock.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class CamelRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:case")
            .autoStartup(false)
            .setHeader("sObjectName").constant("Case")
            .to("salesforce:getSObject?rawPayload=true")
            .to("log:sf?showAll=true");

    }
}
