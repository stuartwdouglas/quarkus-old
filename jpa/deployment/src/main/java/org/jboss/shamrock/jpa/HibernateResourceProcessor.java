package org.jboss.shamrock.jpa;

import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.boot.archive.scan.spi.ClassDescriptor;
import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;
import org.hibernate.protean.impl.PersistenceUnitsHolder;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import javax.inject.Inject;

import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.builditem.BytecodeOutputBuildItem;
import org.jboss.shamrock.deployment.builditem.BytecodeTransformerBuildItem;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.builditem.ResourceBuildItem;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.jpa.runtime.JPADeploymentTemplate;
import org.jboss.shamrock.jpa.runtime.ShamrockScanner;

/**
 * Simulacrum of JPA bootstrap.
 * <p>
 * This does not address the proper integration with Hibernate
 * Rather prepare the path to providing the right metadata
 *
 * @author Emmanuel Bernard emmanuel@hibernate.org
 * @author Sanne Grinovero  <sanne@hibernate.org>
 */
public final class HibernateResourceProcessor {

    @Inject
    BuildProducer<PersistenceUnitDescriptorBuildItem> persistenceProducer;

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    BytecodeOutputBuildItem bytecode;

    @Inject
    BuildProducer<ResourceBuildItem> resources;
    @Inject
    BuildProducer<BytecodeTransformerBuildItem> transformers;

    @Inject
    CombinedIndexBuildItem index;

    @BuildStep
    public void build() throws Exception {

        List<ParsedPersistenceXmlDescriptor> descriptors = PersistenceUnitsHolder.loadOriginalXMLParsedDescriptors();
        for (ParsedPersistenceXmlDescriptor i : descriptors) {
            persistenceProducer.produce(new PersistenceUnitDescriptorBuildItem(i));
        }

        // Hibernate specific reflective classes; these are independent from the model and configuration details.
        HibernateReflectiveNeeds.registerStaticReflectiveNeeds(reflectiveClass);

        JpaJandexScavenger scavenger = new JpaJandexScavenger(reflectiveClass, descriptors, index.getIndex(), bytecode);
        final KnownDomainObjects domainObjects = scavenger.discoverModelAndRegisterForReflection();

        //Modify the bytecode of all entities to enable lazy-loading, dirty checking, etc..
        enhanceEntities(domainObjects, transformers);

        //set up the scanner, as this scanning has already been done we need to just tell it about the classes we
        //have discovered. This scanner is bytecode serializable and is passed directly into the template
        ShamrockScanner scanner = new ShamrockScanner();
        Set<ClassDescriptor> classDescriptors = new HashSet<>();
        for (String i : domainObjects.getClassNames()) {
            ShamrockScanner.ClassDescriptorImpl desc = new ShamrockScanner.ClassDescriptorImpl(i, ClassDescriptor.Categorization.MODEL);
            classDescriptors.add(desc);
        }
        scanner.setClassDescriptors(classDescriptors);
        for (ParsedPersistenceXmlDescriptor i : descriptors) {
            //add resources
            if (i.getProperties().containsKey("javax.persistence.sql-load-script-source")) {
                resources.produce(new ResourceBuildItem((String) i.getProperties().get("javax.persistence.sql-load-script-source")));
            } else {
                resources.produce(new ResourceBuildItem("import.sql"));
            }
        }

        try (BytecodeRecorder recorder = bytecode.addStaticInitTask(RuntimePriority.JPA_DEPLOYMENT)) {
            //now we serialize the XML and class list to bytecode, to remove the need to re-parse the XML on JVM startup
            recorder.registerNonDefaultConstructor(ParsedPersistenceXmlDescriptor.class.getDeclaredConstructor(URL.class), (i) -> Collections.singletonList(i.getPersistenceUnitRootUrl()));
            recorder.getRecordingProxy(JPADeploymentTemplate.class).initMetadata(descriptors, scanner, null);
        }
    }

    private void enhanceEntities(final KnownDomainObjects domainObjects, BuildProducer<BytecodeTransformerBuildItem> transformers) {
        HibernateEntityEnhancer hibernateEntityEnhancer = new HibernateEntityEnhancer();
        for (String i : domainObjects.getClassNames()) {
            transformers.produce(new BytecodeTransformerBuildItem(i, hibernateEntityEnhancer));
        }
    }
}
