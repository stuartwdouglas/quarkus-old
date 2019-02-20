package org.jboss.shamrock.bootstrap;

import java.io.File;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.Result;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.validation.ModelValidator;

public class ShamrockModelBuilderFactory implements ModelBuilder {

    private final ModelBuilder builder;

    public ShamrockModelBuilderFactory() {
         builder = new DefaultModelBuilderFactory().newInstance()
                .setModelValidator(new ModelValidator() {
                    @Override
                    public void validateRawModel(Model model, ModelBuildingRequest request, ModelProblemCollector problems) {

                    }

                    @Override
                    public void validateEffectiveModel(Model model, ModelBuildingRequest request, ModelProblemCollector problems) {

                    }
                });
    }

    @Override
    public ModelBuildingResult build(ModelBuildingRequest request) throws ModelBuildingException {
        return builder.build(request);
    }

    @Override
    public ModelBuildingResult build(ModelBuildingRequest request, ModelBuildingResult result) throws ModelBuildingException {
        return builder.build(request, result);
    }

    @Override
    public Result<? extends Model> buildRawModel(File pomFile, int validationLevel, boolean locationTracking) {
        return builder.buildRawModel(pomFile, validationLevel, locationTracking);
    }
}
