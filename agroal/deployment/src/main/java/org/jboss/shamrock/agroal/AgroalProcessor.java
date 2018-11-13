package org.jboss.shamrock.agroal;

import java.util.Collections;

import javax.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.protean.arc.processor.BeanConfigurator;
import org.jboss.protean.arc.processor.BeanRegistrar;
import org.jboss.shamrock.agroal.runtime.DataSourceCreator;
import org.jboss.shamrock.agroal.runtime.DataSourceDetails;
import org.jboss.shamrock.agroal.runtime.DataSourceProducer;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.Record;
import org.jboss.shamrock.arc.deployment.BeanRegistrarBuildItem;
import org.jboss.shamrock.deployment.buildconfig.BuildConfig;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.BeanContainerBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.recording.BytecodeRecorder;
import org.jboss.shamrock.runtime.ConfiguredValue;

class AgroalProcessor {

    @BuildStep
    AdditionalBeanBuildItem addBeans() {
        return new AdditionalBeanBuildItem(DataSourceProducer.class);
    }

    @BuildStep
    public void build(BuildConfig config,
                      BuildProducer<ReflectiveClassBuildItem> reflectiveClass, BuildProducer<BeanRegistrarBuildItem> beanRegistrars) throws Exception {
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, false,
                io.agroal.pool.ConnectionHandler[].class.getName(),
                io.agroal.pool.ConnectionHandler.class.getName(),
                java.sql.Statement[].class.getName(),
                java.sql.Statement.class.getName(),
                java.sql.ResultSet.class.getName(),
                java.sql.ResultSet[].class.getName()
        ));
        BuildConfig.ConfigNode ds = config.getApplicationConfig().get("datasource");
        if (ds.isNull()) {
            return;
        }
        String driver = ds.get("driver").asString();
        String url = ds.get("url").asString();
        ConfiguredValue configuredDriver = new ConfiguredValue("datasource.driver", driver);
        ConfiguredValue configuredURL = new ConfiguredValue("datasource.url", url);
        if (configuredDriver.getValue() == null) {
            throw new RuntimeException("Driver is required (property 'driver' under 'datasource')");
        }
        if (configuredURL.getValue() == null) {
            throw new RuntimeException("JDBC URL is required (property 'url' under 'datasource')");
        }
        String userName = ds.get("username").asString();
        ConfiguredValue configuredUsername = new ConfiguredValue("datasource.user", userName);
        String password = ds.get("password").asString();
        ConfiguredValue configuredPassword = new ConfiguredValue("datasource.password", password);

        final Integer minSize = ds.get("minSize").asInteger();
        final Integer maxSize = ds.get("maxSize").asInteger();


        reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, driver));

        BeanRegistrar reg = new BeanRegistrar() {
            @Override
            public void register(RegistrationContext registrationContext) {
                BeanConfigurator<DataSourceDetails> res = registrationContext.configure(DataSourceDetails.class);
                res.creator(DataSourceCreator.class);
                res.qualifiers(AnnotationInstance.create(DotName.createSimple(Default.class.getName()), null, Collections.emptyList()));
                res.types(DataSourceDetails.class);
                res.param("driver", configuredDriver.getValue());
                res.param("url", configuredURL.getValue());
                res.param("username", configuredUsername.getValue());
                res.param("password", configuredPassword.getValue());
                if(minSize != null) {
                    res.param("minsize", minSize);
                }
                if(maxSize != null) {
                    res.param("maxsize", maxSize);
                }
                res.done();

            }
        };
        beanRegistrars.produce(new BeanRegistrarBuildItem(reg));

    }
}
