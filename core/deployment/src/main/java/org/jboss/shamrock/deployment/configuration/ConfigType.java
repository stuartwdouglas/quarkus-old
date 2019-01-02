package org.jboss.shamrock.deployment.configuration;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;
import org.jboss.shamrock.runtime.configuration.NameIterator;

/**
 */
public abstract class ConfigType {
    static final MethodDescriptor SRC_CONVERT_METHOD = MethodDescriptor.ofMethod(SmallRyeConfig.class, "convert", Object.class, String.class, Class.class);
    static final MethodDescriptor NI_PREV_METHOD = MethodDescriptor.ofMethod(NameIterator.class, "previous", void.class);
    static final MethodDescriptor NI_NEXT_METHOD = MethodDescriptor.ofMethod(NameIterator.class, "next", void.class);

    /**
     * Containing name.  This is a field name or a map key, <em>not</em> a configuration key segment; as such, it is
     * never {@code null} unless the containing name is intentionally dynamic.
     */
    private final String containingName;
    /**
     * The containing node, or {@code null} if the node is a root.
     */
    private final CompoundConfigType container;
    /**
     * Consume a segment of the name when traversing this node.  Always {@code true} if the containing name is dynamic,
     * otherwise only {@code true} if the node is a configuration group node with an empty relative name.
     */
    private final boolean consumeSegment;

    ConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment) {
        this.containingName = containingName;
        this.container = container;
        this.consumeSegment = consumeSegment;
    }

    static IllegalAccessError toError(final IllegalAccessException e) {
        IllegalAccessError e2 = new IllegalAccessError(e.getMessage());
        e2.setStackTrace(e.getStackTrace());
        return e2;
    }

    static InstantiationError toError(final InstantiationException e) {
        InstantiationError e2 = new InstantiationError(e.getMessage());
        e2.setStackTrace(e.getStackTrace());
        return e2;
    }

    public String getContainingName() {
        return containingName;
    }

    public CompoundConfigType getContainer() {
        return container;
    }

    public <T extends CompoundConfigType> T getContainer(Class<T> expect) {
        final CompoundConfigType container = getContainer();
        if (expect.isInstance(container)) return expect.cast(container);
        throw new IllegalStateException("Container is not a supported type; expected " + expect + " but got " + container.getClass());
    }

    public boolean isConsumeSegment() {
        return consumeSegment;
    }

    /**
     * Load all configuration classes to enable configuration to be instantiated.
     *
     * @throws ClassNotFoundException if a required class was not found
     */
    public void load() throws ClassNotFoundException {
        final CompoundConfigType container = getContainer();
        if (container != null) container.load();
    }

    /**
     * A reusable method which returns an exception that can be thrown when a configuration
     * node is used without its class being loaded.
     *
     * @return the not-loaded exception
     */
    protected static IllegalStateException notLoadedException() {
        return new IllegalStateException("Configuration tree classes not loaded");
    }

    /**
     * Get the default value of this type into the enclosing element.
     *
     * @param name the name of the property whose default value is needed (must not be {@code null})
     * @param enclosing the instance of the enclosing type (must not be {@code null})
     * @param config the configuration (must not be {@code null})
     */
    abstract void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config);

    abstract void generateGetDefaultValueIntoEnclosing(final BytecodeCreator body, final ResultHandle name, final ResultHandle enclosing, final ResultHandle config);

    public abstract ResultHandle writeInitialization(final BytecodeCreator body, final ResultHandle smallRyeConfig);

    /**
     * Get the next-root-most enclosing type, or {@code null} if no such type exists (i.e. this is already a root-most
     * type).
     *
     * @return the next-root-most type, or {@code null} if there is none
     */
    public CompoundConfigType getNextRootType() {
        final CompoundConfigType container = getContainer();
        return container instanceof ConfigDefinition ? null : container.isRoot() ? container : container.getNextRootType();
    }
}


