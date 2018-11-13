package org.jboss.shamrock.jpa.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.hibernate.boot.archive.scan.spi.Scanner;
import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;
import org.hibernate.protean.Hibernate;
import org.hibernate.protean.impl.PersistenceUnitsHolder;
import org.jboss.logging.Logger;
import org.jboss.shamrock.annotations.runtime.Template;
import org.jboss.shamrock.runtime.BeanContainer;

/**
 * @author Emmanuel Bernard emmanuel@hibernate.org
 */
@Template
public class JPADeploymentTemplate {
    private List<String> entities = new ArrayList<>();

    private static final String CONNECTION_URL = "hibernate.connection.url";

    public void addEntity(String entityClass) {
        entities.add(entityClass);
    }

    public void enlistPersistenceUnit() {
        Logger.getLogger("org.jboss.shamrock.jpa").infof("List of entities found by Shamrock deployment:%n%s", entities);
    }

    public void callHibernateFeatureInit() {
        Hibernate.featureInit();
    }

    public Consumer<BeanContainer> initializeJpa(boolean jtaEnabled) {
        return new Consumer<BeanContainer>() {
            @Override
            public void accept(BeanContainer beanContainer) {
                beanContainer.instance(JPAConfig.class).setJtaEnabled(jtaEnabled);
            }
        };
    }

    public Consumer<BeanContainer> registerPersistenceUnit(String unitName) {
        return new Consumer<BeanContainer>() {
            @Override
            public void accept(BeanContainer beanContainer) {
                beanContainer.instance(JPAConfig.class).registerPersistenceUnit(unitName);
            }
        };
    }

    public Consumer<BeanContainer> initDefaultPersistenceUnit() {
        return new Consumer<BeanContainer>() {
            @Override
            public void accept(BeanContainer beanContainer) {
                beanContainer.instance(JPAConfig.class).initDefaultPersistenceUnit();
            }
        };
    }

    public Consumer<BeanContainer> initMetadata(List<ParsedPersistenceXmlDescriptor> parsedPersistenceXmlDescriptors, Scanner scanner) {
        return new Consumer<BeanContainer>() {
            @Override
            public void accept(BeanContainer beanContainer) {
                //this initializes the JPA metadata, and also sets the datasource if no connection URL has been set and a DataSource
                //is available
                if (beanContainer != null) {
                    BeanContainer.Factory<DataSource> ds = beanContainer.instanceFactory(DataSource.class);
                    if (ds != null) {
                        DataSource dataSource = ds.get();
                        for (ParsedPersistenceXmlDescriptor i : parsedPersistenceXmlDescriptors) {
                            if (!i.getProperties().containsKey(CONNECTION_URL)) {
                                i.setJtaDataSource(dataSource);
                            }
                        }
                    }
                }

                PersistenceUnitsHolder.initializeJpa(parsedPersistenceXmlDescriptors, scanner);
            }
        };
    }

    public void startAllUnits(BeanContainer beanContainer) {
        beanContainer.instance(JPAConfig.class).startAll();
    }
}
