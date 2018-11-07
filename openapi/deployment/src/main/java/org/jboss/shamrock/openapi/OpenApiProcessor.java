package org.jboss.shamrock.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.jboss.jandex.IndexView;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import javax.inject.Inject;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.ShamrockConfig;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.ApplicationArchivesBuildItem;
import org.jboss.shamrock.deployment.builditem.BytecodeOutputBuildItem;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.openapi.runtime.OpenApiDeploymentTemplate;
import org.jboss.shamrock.openapi.runtime.OpenApiDocumentProducer;
import org.jboss.shamrock.openapi.runtime.OpenApiServlet;
import org.jboss.shamrock.undertow.ServletData;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiConfigImpl;
import io.smallrye.openapi.runtime.OpenApiStaticFile;
import io.smallrye.openapi.runtime.io.OpenApiSerializer;
import io.smallrye.openapi.runtime.scanner.OpenApiAnnotationScanner;

/**
 * @author Ken Finnigan
 */
@BuildStep
public class OpenApiProcessor implements BuildProcessingStep {

    @Inject
    BuildProducer<AdditionalBeanBuildItem> additionalBean;

    @Inject
    ShamrockConfig config;

    @Inject
    BuildProducer<ServletData> servlets;

    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @Inject
    BytecodeOutputBuildItem bytecode;

    @Inject
    ApplicationArchivesBuildItem archivesBuildItem;

    @Override
    public void build() throws Exception {
        ServletData servletData = new ServletData("openapi", OpenApiServlet.class.getName());
        servletData.getMapings().add(config.getConfig("openapi.path", "/openapi"));
        servlets.produce(servletData);
        additionalBean.produce(new AdditionalBeanBuildItem(OpenApiServlet.class));
        additionalBean.produce(new AdditionalBeanBuildItem(OpenApiDocumentProducer.class));

        Result resourcePath = findStaticModel();

        try (BytecodeRecorder recorder = bytecode.addStaticInitTask(RuntimePriority.WELD_DEPLOYMENT + 30)) {
            OpenApiDeploymentTemplate template = recorder.getRecordingProxy(OpenApiDeploymentTemplate.class);
            OpenAPI sm = generateStaticModel(resourcePath == null ? null : resourcePath.path, resourcePath == null ? OpenApiSerializer.Format.YAML : resourcePath.format);
            OpenAPI am = generateAnnotationModel(combinedIndexBuildItem.getIndex());
            template.setupModel(null, sm, am);
        }
    }


    public OpenAPI generateStaticModel(String resourcePath, OpenApiSerializer.Format format) {
        if (resourcePath != null) {
            try (InputStream is = new URL(resourcePath).openStream()) {
                try (OpenApiStaticFile staticFile = new OpenApiStaticFile(is, format)) {
                    return io.smallrye.openapi.runtime.OpenApiProcessor.modelFromStaticFile(staticFile);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // Ignore
        }

        return null;
    }

    public OpenAPI generateAnnotationModel(IndexView indexView) {
        Config config = ConfigProvider.getConfig();
        OpenApiConfig openApiConfig = new OpenApiConfigImpl(config);
        return new OpenApiAnnotationScanner(openApiConfig, indexView).scan();
    }

    private Result findStaticModel() {
        // Check for the file in both META-INF and WEB-INF/classes/META-INF
        OpenApiSerializer.Format format = OpenApiSerializer.Format.YAML;
        Path resourcePath = archivesBuildItem.getRootArchive().getChildPath("META-INF/openapi.yaml");
        if (resourcePath == null) {
            resourcePath = archivesBuildItem.getRootArchive().getChildPath("WEB-INF/classes/META-INF/openapi.yaml");
        }
        if (resourcePath == null) {
            resourcePath = archivesBuildItem.getRootArchive().getChildPath("META-INF/openapi.yml");
        }
        if (resourcePath == null) {
            resourcePath = archivesBuildItem.getRootArchive().getChildPath("WEB-INF/classes/META-INF/openapi.yml");
        }
        if (resourcePath == null) {
            resourcePath = archivesBuildItem.getRootArchive().getChildPath("META-INF/openapi.json");
            format = OpenApiSerializer.Format.JSON;
        }
        if (resourcePath == null) {
            resourcePath = archivesBuildItem.getRootArchive().getChildPath("WEB-INF/classes/META-INF/openapi.json");
            format = OpenApiSerializer.Format.JSON;
        }

        if (resourcePath == null) {
            return null;
        }

        return new Result(format, archivesBuildItem.getRootArchive().getArchiveRoot().relativize(resourcePath).toString());
    }

    static class Result {
        final OpenApiSerializer.Format format;
        final String path;

        Result(OpenApiSerializer.Format format, String path) {
            this.format = format;
            this.path = path;
        }
    }

}
