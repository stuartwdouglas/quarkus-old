package org.jboss.shamrock.deployment.builditem;

import org.jboss.builder.item.SimpleBuildItem;
import org.jboss.shamrock.deployment.ProcessorContext;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;

//temporary class
public final class BytecodeOutputBuildItem extends SimpleBuildItem {

    private final ProcessorContext processorContext;

    public BytecodeOutputBuildItem(ProcessorContext processorContext) {
        this.processorContext = processorContext;
    }

    /**
     * Adds a new static init task with the given priority. This task will be from a static init
     * block in priority order
     * <p>
     * These tasks are always run before deployment tasks
     *
     * @param priority The priority
     * @return A recorder than can be used to generate bytecode
     */
    public BytecodeRecorder addStaticInitTask(int priority) {
        return processorContext.addStaticInitTask(priority);
    }

    /**
     * Adds a new deployment task with the given priority. This task will be run on startup in priority order.
     *
     * @param priority The priority
     * @return A recorder than can be used to generate bytecode
     */
    public BytecodeRecorder addDeploymentTask(int priority) {
        return processorContext.addDeploymentTask(priority);
    }
}
