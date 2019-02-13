package org.jboss.shamrock.camel.runtime;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.camel.RoutesBuilder;
import org.jboss.shamrock.arc.runtime.BeanContainer;
import org.jboss.shamrock.arc.runtime.BeanContainerListener;
import org.jboss.shamrock.runtime.RuntimeValue;
import org.jboss.shamrock.runtime.ShutdownContext;
import org.jboss.shamrock.runtime.annotations.Template;

@Template
public class CamelTemplate {

    public CamelRuntime init(
            RuntimeValue<?> iruntime,
            RuntimeRegistry registry,
            Properties properties,
            List<RuntimeValue<?>> builders) {
        CamelRuntime runtime = CamelRuntime.class.cast(iruntime.getValue());
        runtime.setRegistry(registry);
        runtime.setProperties(properties);
        runtime.setBuilders(builders.stream()
                .map(RuntimeValue::getValue)
                .map(RoutesBuilder.class::cast)
                .collect(Collectors.toList()));
        runtime.init();
        return runtime;
    }

    public void start(final ShutdownContext shutdown, final CamelRuntime runtime) throws Exception {
        runtime.start();

        //in development mode undertow is started eagerly
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                try {
                    runtime.stop();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public BeanContainerListener initRuntimeInjection(CamelRuntime runtime) {
        return new BeanContainerListener() {
            @Override
            public void created(BeanContainer container) {
                container.instance(CamelRuntimeProducer.class).setCamelRuntime(runtime);
            }
        };
    }
}
