/*
 *
 *   Copyright (c) 2016-2018 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version
 *   2.0 (the "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *   implied.  See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package org.jboss.shamrock.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.fusesource.jansi.Ansi;
import org.jboss.shamrock.cli.commands.AddExtensions;
import org.jboss.shamrock.cli.commands.CreateProject;
import org.jboss.shamrock.maven.components.Prompter;
import org.jboss.shamrock.maven.utilities.MojoUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * This goal helps in setting up Shamrock Maven project with shamrock-maven-plugin, with sensible defaults
 */
@Mojo(name = "create", requiresProject = false)
public class CreateProjectMojo extends AbstractMojo {

    public static final String PLUGIN_KEY = MojoUtils.SHAMROCK_GROUP_ID + ":" + MojoUtils.SHAMROCK_PLUGIN_ARTIFACT_ID;

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter(property = "projectGroupId", defaultValue = "io.jboss.shamrock.sample")
    private String projectGroupId;

    @Parameter(property = "projectArtifactId", defaultValue = "my-shamrock-project")
    private String projectArtifactId;

    @Parameter(property = "projectVersion", defaultValue = "1.0-SNAPSHOT")
    private String projectVersion;

    @Parameter(property = "path", defaultValue = "/hello")
    protected String path;

    @Parameter(property = "className")
    private String className;

    @Parameter(property = "root", defaultValue = "/app")
    private String root;

    @Parameter(property = "extensions")
    private List<String> extensions;

    @Component
    private Prompter prompter;

    @Override
    public void execute() throws MojoExecutionException {
        File projectRoot = new File(".");

        boolean success;
        try {
            sanitizeOptions();

            final Map<String, Object> context = new HashMap<>();
            context.put("className", className);
            context.put("docRoot", root);
            context.put("path", path);

            success = new CreateProject(projectRoot)
                          .groupId(projectGroupId)
                          .artifactId(projectArtifactId)
                          .version(projectVersion)
                          .doCreateProject(context);

            if (success) {
                new AddExtensions(new File(projectRoot, "pom.xml"))
                    .addExtensions(extensions);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        if (success) {
            printUserInstructions(projectRoot);
        }
    }

    private void sanitizeOptions() {
        className = getOrDefault(className, projectGroupId.replace("-", ".")
                                                          .replace("_", ".")
                                            + ".HelloResource");

        if (className.endsWith(MojoUtils.JAVA_EXTENSION)) {
            className = className.substring(0, className.length() - MojoUtils.JAVA_EXTENSION.length());
        }

        if (!root.startsWith("/")) {
            root = "/" + root;
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }
    }

    private String getOrDefault(final String parameter, final String defaultValue) {
        return parameter != null ? parameter : defaultValue;
    }

    private void printUserInstructions(File root) {
        getLog().info("");
        getLog().info("========================================================================================");
        getLog().info(ansi().a("Your new application has been created in ").bold().a(root.getAbsolutePath()).boldOff().toString());
        getLog().info(ansi().a("Navigate into this directory and launch your application with ")
                            .bold()
                            .fg(Ansi.Color.CYAN)
                            .a("mvn compile shamrock:dev")
                            .reset()
                            .toString());
        getLog().info(
            ansi().a("Your application will be accessible on ").bold().fg(Ansi.Color.CYAN).a("http://localhost:8080").reset().toString());
        getLog().info("========================================================================================");
        getLog().info("");
    }
}
