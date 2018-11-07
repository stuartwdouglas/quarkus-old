package org.jboss.shamrock.undertow.runtime;

import java.io.Closeable;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.jboss.shamrock.runtime.ConfiguredValue;
import org.jboss.shamrock.runtime.ContextObject;
import org.jboss.shamrock.runtime.InjectionInstance;
import org.jboss.shamrock.runtime.StartupContext;
import org.xnio.Options;

import io.undertow.Undertow;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.session.SessionIdGenerator;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;

/**
 * Provides the runtime methods to bootstrap Undertow. This class is present in the final uber-jar,
 * and is invoked from generated bytecode
 */
@Template
public class UndertowDeploymentTemplate {

    private static final Logger log = Logger.getLogger(UndertowDeploymentTemplate.class.getName());

    public static final HttpHandler ROOT_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            currentRoot.handleRequest(exchange);
        }
    };
    private static final String RESOURCES_PROP = "shamrock.undertow.resources";

    private static volatile Undertow undertow;
    private static volatile HttpHandler currentRoot;

    @ContextObject("deploymentInfo")
    public DeploymentInfoBuildItem createDeployment(String name, Set<String> knownFile, Set<String> knownDirectories) {
        DeploymentInfo d = new DeploymentInfo();
        d.setSessionIdGenerator(new ShamrockSessionIdGenerator());
        d.setClassLoader(getClass().getClassLoader());
        d.setDeploymentName(name);
        d.setContextPath("/");
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = new ClassLoader() {
            };
        }
        d.setClassLoader(cl);
        //TODO: this is a big hack
        String resourcesDir = System.getProperty(RESOURCES_PROP);
        if (resourcesDir == null) {
            d.setResourceManager(new KnownPathResourceManager(knownFile, knownDirectories, new ClassPathResourceManager(d.getClassLoader(), "META-INF/resources")));
        } else {
            d.setResourceManager(new PathResourceManager(Paths.get(resourcesDir)));
        }
        d.addWelcomePages("index.html", "index.htm");
        return d;
    }

    public <T> InstanceFactory<T> createInstanceFactory(InjectionInstance<T> injectionInstance) {
        return new ShamrockInstanceFactory<T>(injectionInstance);
    }

    public AtomicReference<ServletInfo> registerServlet(@ContextObject("deploymentInfo") DeploymentInfo info,
                                                        String name,
                                                        Class<?> servletClass,
                                                        boolean asyncSupported,
                                                        int loadOnStartup,
                                                        InstanceFactory<? extends Servlet> instanceFactory) throws Exception {
        ServletInfo servletInfo = new ServletInfo(name, (Class<? extends Servlet>) servletClass, instanceFactory);
        info.addServlet(servletInfo);
        servletInfo.setAsyncSupported(asyncSupported);
        if (loadOnStartup > 0) {
            servletInfo.setLoadOnStartup(loadOnStartup);
        }
        return new AtomicReference<>(servletInfo);
    }

    public void addServletInitParam(AtomicReference<ServletInfo> info, String name, String value) {
        info.get().addInitParam(name, value);
    }

    public void addServletMapping(@ContextObject("deploymentInfo") DeploymentInfo info, String name, String mapping) throws Exception {
        ServletInfo sv = info.getServlets().get(name);
        sv.addMapping(mapping);
    }

    public void setMultipartConfig(AtomicReference<ServletInfo> sref, String location, long fileSize, long maxRequestSize, int fileSizeThreshold) {
        MultipartConfigElement mp = new MultipartConfigElement(location, fileSize, maxRequestSize, fileSizeThreshold);
        sref.get().setMultipartConfig(mp);
    }

    public AtomicReference<FilterInfo> registerFilter(@ContextObject("deploymentInfo") DeploymentInfo info,
                                                      String name, Class<?> filterClass,
                                                      boolean asyncSupported,
                                                      InstanceFactory<? extends Filter> instanceFactory) throws Exception {
        FilterInfo filterInfo = new FilterInfo(name, (Class<? extends Filter>) filterClass, instanceFactory);
        info.addFilter(filterInfo);
        filterInfo.setAsyncSupported(asyncSupported);
        return new AtomicReference<>(filterInfo);
    }

    public void addFilterInitParam(AtomicReference<FilterInfo> info, String name, String value) {
        info.get().addInitParam(name, value);
    }

    public void addFilterMapping(@ContextObject("deploymentInfo") DeploymentInfo info, String name, String mapping, DispatcherType dispatcherType) throws Exception {
        info.addFilterUrlMapping(name, mapping, dispatcherType);
    }


    public void registerListener(@ContextObject("deploymentInfo") DeploymentInfo info, Class<?> listenerClass, InstanceFactory<? extends EventListener> factory) {
        info.addListener(new ListenerInfo((Class<? extends EventListener>) listenerClass, factory));
    }

    public void addServletContextParameter(@ContextObject("deploymentInfo") DeploymentInfo info, String name, String value) {
        info.addInitParameter(name, value);
    }

    @ContextObject("undertow.handler-wrappers")
    public List<HandlerWrapper> initHandlerWrappers() {
        return new ArrayList<>();
    }

    public void startUndertow(StartupContext startupContext, @ContextObject("servletHandler") HttpHandler handler, ConfiguredValue port, ConfiguredValue host, ConfiguredValue ioThreads, ConfiguredValue workerThreads,@ContextObject("undertow.handler-wrappers") List<HandlerWrapper> wrappers) throws ServletException {
        if (undertow == null) {
            startUndertowEagerly(port, host, ioThreads, workerThreads, null);

            //in development mode undertow is started eagerly
            startupContext.addCloseable(new Closeable() {
                @Override
                public void close() {
                    undertow.stop();
                }
            });
        }
        HttpHandler main = handler;
        for(HandlerWrapper i : wrappers) {
            main = i.wrap(main);
        }
        currentRoot = main;
    }


    /**
     * Used for shamrock:run, where we want undertow to start very early in the process.
     * <p>
     * This enables recovery from errors on boot. In a normal boot undertow is one of the last things start, so there would
     * be no chance to use hot deployment to fix the error. In development mode we start Undertow early, so any error
     * on boot can be corrected via the hot deployment handler
     */
    public void startUndertowEagerly(ConfiguredValue port, ConfiguredValue host, ConfiguredValue ioThreads, ConfiguredValue workerThreads, HandlerWrapper hotDeploymentWrapper) throws ServletException {
        if (undertow == null) {
            log.log(Level.INFO, "Starting Undertow on port " + port.getValue());
            HttpHandler rootHandler = new CanonicalPathHandler(ROOT_HANDLER);
            if (hotDeploymentWrapper != null) {
                rootHandler = hotDeploymentWrapper.wrap(rootHandler);
            }

            Undertow.Builder builder = Undertow.builder()
                    .addHttpListener(Integer.parseInt(port.getValue()), host.getValue())
                    .setHandler(rootHandler);
            if(!ioThreads.getValue().equals("")) {
                builder.setIoThreads(Integer.parseInt(ioThreads.getValue()));
            }
            if(!workerThreads.getValue().equals("")) {
                builder.setWorkerThreads(Integer.parseInt(workerThreads.getValue()));
            }
            undertow = builder
                    .build();
            undertow.start();
        }
    }


    @ContextObject("servletHandler")
    public HttpHandler bootServletContainer(@ContextObject("deploymentInfo") DeploymentInfo info) {
        try {
            ServletContainer servletContainer = Servlets.defaultContainer();
            DeploymentManager manager = servletContainer.addDeployment(info);
            manager.deploy();
            return manager.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * we can't have SecureRandom in the native image heap, so we need to lazy init
     */
    private static class ShamrockSessionIdGenerator implements SessionIdGenerator {


        private volatile SecureRandom random;

        private volatile int length = 30;

        private static final char[] SESSION_ID_ALPHABET;

        private static final String ALPHABET_PROPERTY = "io.undertow.server.session.SecureRandomSessionIdGenerator.ALPHABET";

        static {
            String alphabet = System.getProperty(ALPHABET_PROPERTY, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_");
            if (alphabet.length() != 64) {
                throw new RuntimeException("io.undertow.server.session.SecureRandomSessionIdGenerator must be exactly 64 characters long");
            }
            SESSION_ID_ALPHABET = alphabet.toCharArray();
        }

        @Override
        public String createSessionId() {
            if (random == null) {
                random = new SecureRandom();
            }
            final byte[] bytes = new byte[length];
            random.nextBytes(bytes);
            return new String(encode(bytes));
        }


        public int getLength() {
            return length;
        }

        public void setLength(final int length) {
            this.length = length;
        }

        /**
         * Encode the bytes into a String with a slightly modified Base64-algorithm
         * This code was written by Kevin Kelley <kelley@ruralnet.net>
         * and adapted by Thomas Peuss <jboss@peuss.de>
         *
         * @param data The bytes you want to encode
         * @return the encoded String
         */
        private char[] encode(byte[] data) {
            char[] out = new char[((data.length + 2) / 3) * 4];
            char[] alphabet = SESSION_ID_ALPHABET;
            //
            // 3 bytes encode to 4 chars.  Output is always an even
            // multiple of 4 characters.
            //
            for (int i = 0, index = 0; i < data.length; i += 3, index += 4) {
                boolean quad = false;
                boolean trip = false;

                int val = (0xFF & (int) data[i]);
                val <<= 8;
                if ((i + 1) < data.length) {
                    val |= (0xFF & (int) data[i + 1]);
                    trip = true;
                }
                val <<= 8;
                if ((i + 2) < data.length) {
                    val |= (0xFF & (int) data[i + 2]);
                    quad = true;
                }
                out[index + 3] = alphabet[(quad ? (val & 0x3F) : 63)];
                val >>= 6;
                out[index + 2] = alphabet[(trip ? (val & 0x3F) : 63)];
                val >>= 6;
                out[index + 1] = alphabet[val & 0x3F];
                val >>= 6;
                out[index] = alphabet[val & 0x3F];
            }
            return out;
        }
    }
}
