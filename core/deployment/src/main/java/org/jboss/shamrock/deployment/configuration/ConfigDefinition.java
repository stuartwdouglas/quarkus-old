package org.jboss.shamrock.deployment.configuration;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.TreeMap;

import io.smallrye.config.SmallRyeConfig;
import org.wildfly.common.Assert;

/**
 * A configuration definition.  This class represents the configuration space as trees of nodes, where each tree
 * has a root which recursively contains all of the elements within the configuration.
 */
public class ConfigDefinition extends MapConfigType {
    private final TreeMap<String, Object> rootNodes = new TreeMap<>();
    private final ConfigPatternMap<LeafConfigType> leafPatterns = new ConfigPatternMap<>();
    private final IdentityHashMap<Object, ValueInfo> realizedInstances = new IdentityHashMap<>();
    private final IdentityHashMap<Class<?>, CompoundConfigType> loadedTypes = new IdentityHashMap<>();
    private final Set<CompoundConfigType> realizedTypes = Collections.newSetFromMap(new IdentityHashMap<>());

    public ConfigDefinition() {
        super(null, null, false);
    }

    TreeMap<String, Object> getOrCreate(final ConfigPropertyName name, final SmallRyeConfig config) {
        return rootNodes;
    }

    void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config) {
        throw Assert.unsupported();
    }

    public void load() throws ClassNotFoundException {
        loadFrom(leafPatterns);
    }

    void registerInstance(final String key, final CompoundConfigType type, final Object instance) {
        realizedInstances.put(instance, new ValueInfo(key, type));
    }

    public boolean isRoot() {
        return true;
    }

    public boolean areChildrenRoots() {
        return true;
    }

    void registerClass(final CompoundConfigType type, final Class<?> clazz) {
        loadedTypes.put(clazz, type);
    }

    public void loadConfiguration(SmallRyeConfig config) {
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith("shamrock.")) {
                propertyName = propertyName.substring("shamrock.".length());
                final LeafConfigType leafType = leafPatterns.match(propertyName);
                final ConfigPropertyName name = ConfigPropertyName.fromString(propertyName);
                if (leafType != null) {
                    leafType.acceptConfigurationValue(name, config);
                } else {
                    // TODO: log.warnf("Unknown configuration key \"shamrock.%s\" provided", name);
                }
            }
        }
    }

    public Set<CompoundConfigType> getRealizedTypes() {
        return realizedTypes;
    }

    public CompoundConfigType getTypeOfRealizedInstance(Object instance) {
        final ValueInfo valueInfo = realizedInstances.get(instance);
        return valueInfo == null ? null : valueInfo.getResolvedType();
    }

    public CompoundConfigType getTypeOfLoadedClass(Class<?> clazz) {
        return loadedTypes.get(clazz);
    }

    public ConfigPatternMap<LeafConfigType> getLeafPatterns() {
        return leafPatterns;
    }

    private void loadFrom(ConfigPatternMap<LeafConfigType> map) throws ClassNotFoundException {
        final LeafConfigType matched = map.getMatched();
        if (matched != null) {
            matched.load();
        }
        for (String name : map.childNames()) {
            loadFrom(map.getChild(name));
        }
    }

    public Object getRealizedInstance(final String address) {
        return rootNodes.get(address);
    }

    public static final class ValueInfo {
        private final String key;
        private final CompoundConfigType resolvedType;

        public ValueInfo(final String key, final CompoundConfigType resolvedType) {
            this.key = key;
            this.resolvedType = resolvedType;
        }

        public String getKey() {
            return key;
        }

        public CompoundConfigType getResolvedType() {
            return resolvedType;
        }
    }
}
