package org.jboss.shamrock.transactions;

import java.util.Properties;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import javax.inject.Inject;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.Capabilities;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.BeanArchiveIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.BytecodeOutputBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.builditem.RuntimeInitializedClassBuildItem;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.runtime.ConfiguredValue;
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

@BuildStep(providesCapabilities = Capabilities.TRANSACTIONS)
class TransactionsProcessor implements BuildProcessingStep {

    @Inject
    BuildProducer<AdditionalBeanBuildItem> additionalBeans;

    @Inject
    BeanArchiveIndexBuildItem beanArchiveIndex;

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    BytecodeOutputBuildItem bytecode;

    @Inject
    BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit;

    @Override
    public void build() throws Exception {
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
        try (BytecodeRecorder bc = bytecode.addStaticInitTask(RuntimePriority.TRANSACTIONS_DEPLOYMENT)) {
            TransactionTemplate tt = bc.getRecordingProxy(TransactionTemplate.class);
            Properties defaultProperties = PropertiesFactory.getDefaultProperties();
            tt.setDefaultProperties(defaultProperties);
            tt.setNodeName(new ConfiguredValue("transactions.node-name", "shamrock"));
        }

    }
}
