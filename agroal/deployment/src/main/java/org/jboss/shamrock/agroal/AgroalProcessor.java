package org.jboss.shamrock.agroal;

import org.jboss.shamrock.agroal.runtime.DataSourceProducer;
import org.jboss.shamrock.agroal.runtime.DataSourceTemplate;
import org.jboss.shamrock.annotations.BuildProcessor;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.buildconfig.BuildConfig;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.BytecodeOutputBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.runtime.ConfiguredValue;

@BuildProcessor
class AgroalProcessor implements BuildProcessingStep {

    @BuildResource
    BuildProducer<AdditionalBeanBuildItem> additionalBean;

    @BuildResource
    BytecodeOutputBuildItem bytecode;

    @BuildResource
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @BuildResource
    BuildConfig config;

    @Override
    public void build() throws Exception {
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
        additionalBean.produce(new AdditionalBeanBuildItem(DataSourceProducer.class));
        try (BytecodeRecorder bc = bytecode.addDeploymentTask(RuntimePriority.DATASOURCE_DEPLOYMENT)) {
            DataSourceTemplate template = bc.getRecordingProxy(DataSourceTemplate.class);
            template.addDatasource(null, configuredURL.getValue(), bc.classProxy(configuredDriver.getValue()), configuredUsername.getValue(), configuredPassword.getValue(), minSize, maxSize);
        }
    }
}
