/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.shamrock.magic;

import java.util.Arrays;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.deployment.builditem.BytecodeTransformerBuildItem;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.jpa.AdditionalJpaModelBuildItem;
import org.jboss.shamrock.magic.runtime.Model;

/**
 */
public final class ModelResourceProcessor {

    private static final DotName DOTNAME_MODEL = DotName.createSimple(Model.class.getName());
    
    @BuildStep
    List<AdditionalJpaModelBuildItem> produceModel() {
        // only useful for the index resolution: hibernate will register it to be transformed, but BuildMojo
        // only transforms classes from the application jar, so we do our own transforming
        return Arrays.asList(
                new AdditionalJpaModelBuildItem(Model.class));
    }
    
    @BuildStep
    void build(CombinedIndexBuildItem index,
                      BuildProducer<BytecodeTransformerBuildItem> transformers) throws Exception {

        ModelEnhancer modelEnhancer = new ModelEnhancer();
        for (ClassInfo classInfo : index.getIndex().getKnownDirectSubclasses(DOTNAME_MODEL)) {
            System.err.println("Scanning model class for bytecode work: "+classInfo);
            transformers.produce(new BytecodeTransformerBuildItem(classInfo.name().toString(), modelEnhancer));
        }
    }
}
