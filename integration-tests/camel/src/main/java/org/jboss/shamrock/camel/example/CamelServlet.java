package org.jboss.shamrock.camel.example;

import java.util.List;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.camel.Route;
import org.jboss.shamrock.camel.runtime.CamelRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
@ApplicationScoped
public class CamelServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelServlet.class);

    @Inject
    CamelRuntime runtime;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public List<String> getRoutes() {
        return runtime.getContext().getRoutes().stream().map(Route::getId).collect(Collectors.toList());
    }
}