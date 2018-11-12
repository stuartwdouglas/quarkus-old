package org.jboss.shamrock.maven.runner;

import java.io.File;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.Record;
import org.jboss.shamrock.annotations.runtime.Template;
import org.jboss.shamrock.runtime.ConfiguredValue;
import org.jboss.shamrock.undertow.runtime.UndertowDeploymentTemplate;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;

public class RuntimeCompilationSetup {

    private static final Logger log = Logger.getLogger(RuntimeCompilationTemplate.class.getName());

    @BuildStep
    @Record(staticInit = true)
    public void build(RuntimeCompilationTemplate runtimeCompilationTemplate, UndertowDeploymentTemplate undertowDeploymentTemplate) throws Exception {
        try {
            //don't do this if we don't have Undertow
            Class.forName("org.jboss.shamrock.undertow.ServletResourceProcessor");
        } catch (ClassNotFoundException e) {
            return;
        }
        String classesDir = System.getProperty("shamrock.runner.classes");
        if (classesDir != null) {
            HandlerWrapper wrapper = runtimeCompilationTemplate.createHandlerWrapper();
            undertowDeploymentTemplate.startUndertowEagerly(new ConfiguredValue("http.port", "8080"), new ConfiguredValue("http.host", "localhost"), new ConfiguredValue("http.io-threads", ""), new ConfiguredValue("http.worker-threads", ""), wrapper);
        }
    }


    @Template
    public static class RuntimeCompilationTemplate {

        public HandlerWrapper createHandlerWrapper() {

            String classesDir = System.getProperty("shamrock.runner.classes");
            String sourcesDir = System.getProperty("shamrock.runner.sources");

            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler handler) {
                    ClassLoaderCompiler compiler = null;
                    try {
                        compiler = new ClassLoaderCompiler(Thread.currentThread().getContextClassLoader(), new File(classesDir));
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Failed to create compiler, runtime compilation will be unavailable", e);
                        return handler;
                    }
                    return new RuntimeUpdatesHandler(handler, Paths.get(classesDir), Paths.get(sourcesDir), compiler);
                }
            };
        }

    }
}
