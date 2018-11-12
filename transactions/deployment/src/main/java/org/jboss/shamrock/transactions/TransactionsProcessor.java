package org.jboss.shamrock.transactions;

import java.util.Properties;

import javax.inject.Inject;

import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.Record;
import org.jboss.shamrock.deployment.Capabilities;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.BeanArchiveIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.builditem.RuntimeInitializedClassBuildItem;
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

class TransactionsProcessor {

    @Inject
    BuildProducer<AdditionalBeanBuildItem> additionalBeans;

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit;

    @BuildStep(providesCapabilities = Capabilities.TRANSACTIONS)
    @Record(staticInit = true)
    public void build(TransactionTemplate tt) throws Exception {
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
        tt.setNodeName(new ConfiguredValue("transactions.node-name", "shamrock"));

    }
}
