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

package org.jboss.shamrock.health.runtime;

import javax.servlet.ServletContext;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shamrock.runtime.Template;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;

@Template
public class HealthTemplate {

    /**
     * The path to the health check servlet
     */
    @ConfigProperty(name = "shamrock.health.path", defaultValue = "/health")
    String path;


    public ServletExtension getExtension() {
        return new ServletExtension() {
            @Override
            public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                ServletInfo info = new ServletInfo("health", HealthServlet.class);
                info.addMapping(path);
                deploymentInfo.addServlet(info);
            }
        };
    }
}
