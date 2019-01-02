package org.jboss.shamrock.deployment.configuration;

import java.util.Objects;

/**
 * A configuration property name or name segment, as matched by pattern match.
 */
public final class ConfigPropertyName {
    private final ConfigPropertyName parent;
    private final String segmentName;
    private String toString;

    private ConfigPropertyName(final ConfigPropertyName parent, final String segmentName) {
        this.parent = parent;
        this.segmentName = segmentName;
    }

    public static ConfigPropertyName fromString(String str) {
        return fromString(null, str, 0);
    }

    private static ConfigPropertyName fromString(ConfigPropertyName parent, String str, int offs) {
        final int idx = str.indexOf('.');
        if (idx == -1) {
            return new ConfigPropertyName(parent, str.substring(offs));
        }
        return fromString(new ConfigPropertyName(parent, str.substring(offs, idx)), str, idx + 1);
    }

    public ConfigPropertyName getParent() {
        return parent;
    }

    public String getSegmentName() {
        return segmentName;
    }

    public boolean equals(final Object other) {
        return this == other || other instanceof ConfigPropertyName && equals((ConfigPropertyName) other);
    }

    public boolean equals(final ConfigPropertyName other) {
        return Objects.equals(parent, other.parent) && Objects.equals(segmentName, other.segmentName);
    }

    public int hashCode() {
        return Objects.hash(parent, segmentName);
    }

    public String toString() {
        final String toString = this.toString;
        if (toString == null) return this.toString = parent == null ? segmentName : parent.toString() + "." + segmentName;
        return toString;
    }
}
