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

package org.jboss.shamrock.transactions;

import static org.jboss.shamrock.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.Properties;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shamrock.deployment.annotations.BuildProducer;
import org.jboss.shamrock.deployment.annotations.BuildStep;
import org.jboss.shamrock.deployment.annotations.Record;
import org.jboss.shamrock.deployment.Capabilities;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.RuntimeInitializedClassBuildItem;
import org.jboss.shamrock.transactions.runtime.TransactionProducers;
import org.jboss.shamrock.transactions.runtime.TransactionTemplate;
import org.jboss.shamrock.transactions.runtime.interceptor.TransactionalInterceptorMandatory;
import org.jboss.shamrock.transactions.runtime.interceptor.TransactionalInterceptorNever;
import org.jboss.shamrock.transactions.runtime.interceptor.TransactionalInterceptorNotSupported;
import org.jboss.shamrock.transactions.runtime.interceptor.TransactionalInterceptorRequired;
import org.jboss.shamrock.transactions.runtime.interceptor.TransactionalInterceptorRequiresNew;
import org.jboss.shamrock.transactions.runtime.interceptor.TransactionalInterceptorSupports;

import com.arjuna.ats.internal.arjuna.coordinator.CheckedActionFactoryImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.UserTransactionImple;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.util.propertyservice.PropertiesFactory;

class TransactionsProcessor {

    @Inject
    BuildProducer<AdditionalBeanBuildItem> additionalBeans;

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit;

    /**
     * The node name used by the transaction manager
     */
    @ConfigProperty(name = "shamrock.transactions.node-name", defaultValue = "shamrock")
    String nodeName;

    @BuildStep(providesCapabilities = Capabilities.TRANSACTIONS)
    @Record(STATIC_INIT)
    public void build(TransactionTemplate tt) {
        additionalBeans.produce(new AdditionalBeanBuildItem(TransactionProducers.class));
        runtimeInit.produce(new RuntimeInitializedClassBuildItem("com.arjuna.ats.internal.jta.resources.arjunacore.CommitMarkableResourceRecord"));
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, JTAEnvironmentBean.class.getName(),
                UserTransactionImple.class.getName(),
                CheckedActionFactoryImple.class.getName(),
                TransactionManagerImple.class.getName(),
                TransactionSynchronizationRegistryImple.class.getName()));

        additionalBeans.produce(new AdditionalBeanBuildItem(TransactionalInterceptorSupports.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(TransactionalInterceptorNever.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(TransactionalInterceptorRequired.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(TransactionalInterceptorRequiresNew.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(TransactionalInterceptorMandatory.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(TransactionalInterceptorNotSupported.class));

        //we want to force Arjuna to init at static init time
        Properties defaultProperties = PropertiesFactory.getDefaultProperties();
        tt.setDefaultProperties(defaultProperties);
        tt.setNodeName(nodeName);

    }
}
