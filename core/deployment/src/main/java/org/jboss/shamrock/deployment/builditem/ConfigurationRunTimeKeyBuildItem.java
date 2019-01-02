package org.jboss.shamrock.deployment.builditem;

import org.jboss.builder.item.MultiBuildItem;
import org.jboss.shamrock.deployment.annotations.ExecutionTime;
import org.jboss.shamrock.deployment.configuration.CompoundConfigType;
import org.wildfly.common.Assert;

/**
 * A configuration key which is used by the extension at run time.
 */
public class ConfigurationRunTimeKeyBuildItem extends MultiBuildItem {
    private final String baseAddress;
    private final ExecutionTime executionTime;
    private final CompoundConfigType expectedType;

    public ConfigurationRunTimeKeyBuildItem(final String baseAddress, final ExecutionTime executionTime, final CompoundConfigType expectedType) {
        Assert.checkNotNullParam("baseAddress", baseAddress);
        Assert.checkNotNullParam("executionTime", executionTime);
        Assert.checkNotNullParam("expectedType", expectedType);
        this.baseAddress = baseAddress;
        this.executionTime = executionTime;
        this.expectedType = expectedType;
    }

    public String getBaseAddress() {
        return baseAddress;
    }

    public ExecutionTime getExecutionTime() {
        return executionTime;
    }

    public CompoundConfigType getExpectedType() {
        return expectedType;
    }
}
