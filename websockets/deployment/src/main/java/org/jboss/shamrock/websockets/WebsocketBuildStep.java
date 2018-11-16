package org.jboss.shamrock.websockets;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.ClientEndpoint;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.ExecutionTime;
import org.jboss.shamrock.annotations.Record;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.ReflectiveClassBuildItem;
import org.jboss.shamrock.undertow.ServletContextAttributeBuildItem;
import org.jboss.shamrock.websockets.runtime.WebsocketTemplate;

import io.undertow.websockets.jsr.WebSocketDeploymentInfo;

public class WebsocketBuildStep {


    private static final DotName SERVER_ENDPOINT = DotName.createSimple(ServerEndpoint.class.getName());
    private static final DotName CLIENT_ENDPOINT = DotName.createSimple(ClientEndpoint.class.getName());
    private static final DotName SERVER_APPLICATION_CONFIG = DotName.createSimple(ServerApplicationConfig.class.getName());
    private static final DotName ENDPOINT = DotName.createSimple(Endpoint.class.getName());


    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT, name = "Websockets")
    public ServletContextAttributeBuildItem deploy(final CombinedIndexBuildItem indexBuildItem,
                                                   WebsocketTemplate template, BuildProducer<ReflectiveClassBuildItem> reflection) throws Exception {

        final Set<String> annotatedEndpoints = new HashSet<>();
        final Set<String> endpoints = new HashSet<>();
        final Set<String> config = new HashSet<>();

        final IndexView index = indexBuildItem.getIndex();

        final Collection<AnnotationInstance> serverEndpoints = index.getAnnotations(SERVER_ENDPOINT);
        for (AnnotationInstance endpoint : serverEndpoints) {
            if (endpoint.target() instanceof ClassInfo) {
                ClassInfo clazz = (ClassInfo) endpoint.target();
                if (!Modifier.isAbstract(clazz.flags())) {
                    annotatedEndpoints.add(clazz.name().toString());
                }
            }
        }

        final Collection<AnnotationInstance> clientEndpoints = index.getAnnotations(CLIENT_ENDPOINT);
        for (AnnotationInstance endpoint : clientEndpoints) {
            if (endpoint.target() instanceof ClassInfo) {
                ClassInfo clazz = (ClassInfo) endpoint.target();
                if (!Modifier.isAbstract(clazz.flags())) {
                    annotatedEndpoints.add(clazz.name().toString());
                }
            }
        }

        final Collection<ClassInfo> subclasses = index.getAllKnownImplementors(SERVER_APPLICATION_CONFIG);

        for (final ClassInfo clazz : subclasses) {
            if (!Modifier.isAbstract(clazz.flags())) {
                config.add(clazz.name().toString());
            }
        }

        final Collection<ClassInfo> epClasses = index.getAllKnownSubclasses(ENDPOINT);

        for (final ClassInfo clazz : epClasses) {
            if (!Modifier.isAbstract(clazz.flags())) {
                endpoints.add(clazz.name().toString());
            }
        }
        if (annotatedEndpoints.isEmpty() &&
                endpoints.isEmpty() &&
                config.isEmpty()) {
            return null;
        }
        reflection.produce(new ReflectiveClassBuildItem(true, false , annotatedEndpoints.toArray(new String[annotatedEndpoints.size()])));
        
        return new ServletContextAttributeBuildItem(WebSocketDeploymentInfo.ATTRIBUTE_NAME, template.createDeploymentInfo(annotatedEndpoints, endpoints, config));
    }
}
