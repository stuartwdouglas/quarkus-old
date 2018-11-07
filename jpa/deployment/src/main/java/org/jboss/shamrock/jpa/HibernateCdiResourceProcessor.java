package org.jboss.shamrock.jpa;

import java.util.List;

import javax.enterprise.inject.Produces;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.BeanDeployment;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.Capabilities;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.BytecodeOutputBuildItem;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.GeneratedResourceBuildItem;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.jpa.runtime.DefaultEntityManagerFactoryProducer;
import org.jboss.shamrock.jpa.runtime.DefaultEntityManagerProducer;
import org.jboss.shamrock.jpa.runtime.JPAConfig;
import org.jboss.shamrock.jpa.runtime.JPADeploymentTemplate;
import org.jboss.shamrock.jpa.runtime.TransactionEntityManagers;

@BuildStep
class HibernateCdiResourceProcessor implements BuildProcessingStep {

    private static final DotName PERSISTENCE_CONTEXT = DotName.createSimple(PersistenceContext.class.getName());
    private static final DotName PERSISTENCE_UNIT = DotName.createSimple(PersistenceUnit.class.getName());
    private static final DotName PRODUCES = DotName.createSimple(Produces.class.getName());

    @BuildResource
    BytecodeOutputBuildItem bytecode;

    @BuildResource
    List<PersistenceUnitDescriptorBuildItem> descriptors;

    @BuildResource
    Capabilities capabilities;

    @BuildResource
    BuildProducer<AdditionalBeanBuildItem> additionalBeans;

    @BuildResource
    BuildProducer<GeneratedResourceBuildItem> resources;

    @BuildResource
    BeanDeployment beanDeployment;

    @BuildResource
    CombinedIndexBuildItem combinedIndex;

    @Override
    public void build() throws Exception {

        try (BytecodeRecorder recorder = bytecode.addDeploymentTask(RuntimePriority.BOOTSTRAP_EMF)) {
            JPADeploymentTemplate template = recorder.getRecordingProxy(JPADeploymentTemplate.class);

            additionalBeans.produce(new AdditionalBeanBuildItem(JPAConfig.class, TransactionEntityManagers.class));

            if (capabilities.isCapabilityPresent(Capabilities.CDI_ARC)) {
                resources.produce(new GeneratedResourceBuildItem("META-INF/services/org.jboss.protean.arc.ResourceReferenceProvider",
                        "org.jboss.shamrock.jpa.runtime.JPAResourceReferenceProvider".getBytes()));
                beanDeployment.addResourceAnnotation(PERSISTENCE_CONTEXT);
                beanDeployment.addResourceAnnotation(PERSISTENCE_UNIT);
            }

            template.initializeJpa(null, capabilities.isCapabilityPresent(Capabilities.TRANSACTIONS));

            // Bootstrap all persistence units
            for (PersistenceUnitDescriptorBuildItem persistenceUnitDescriptor : descriptors) {
                template.bootstrapPersistenceUnit(null, persistenceUnitDescriptor.getDescriptor().getName());
            }
            template.initDefaultPersistenceUnit(null);

            if (descriptors.size() == 1) {
                // There is only one persistence unit - register CDI beans for EM and EMF if no
                // producers are defined
                if (isUserDefinedProducerMissing(combinedIndex.getIndex(), PERSISTENCE_UNIT)) {
                    additionalBeans.produce(new AdditionalBeanBuildItem(DefaultEntityManagerFactoryProducer.class));
                }
                if (isUserDefinedProducerMissing(combinedIndex.getIndex(), PERSISTENCE_CONTEXT)) {
                    additionalBeans.produce(new AdditionalBeanBuildItem(DefaultEntityManagerProducer.class));
                }
            }
        }
    }

    private boolean isUserDefinedProducerMissing(IndexView index, DotName annotationName) {
        for (AnnotationInstance annotationInstance : index.getAnnotations(annotationName)) {
            if (annotationInstance.target().kind() == AnnotationTarget.Kind.METHOD) {
                if (annotationInstance.target().asMethod().hasAnnotation(PRODUCES)) {
                    return false;
                }
            } else if (annotationInstance.target().kind() == AnnotationTarget.Kind.FIELD) {
                for (AnnotationInstance i : annotationInstance.target().asField().annotations()) {
                    if (i.name().equals(PRODUCES)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
