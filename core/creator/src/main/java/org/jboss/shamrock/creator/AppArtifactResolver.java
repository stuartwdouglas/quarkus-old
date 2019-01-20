/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.shamrock.creator;

import java.nio.file.Path;
import java.util.List;

/**
 * Artifact resolver used to resolve application and/or its dependency artifacts.
 *
 * @author Alexey Loubyansky
 */
public interface AppArtifactResolver {

  /**
   * (Re-)links an artifact to a path.
   *
   * @param appArtifact an artifact to (re-)link to the path
   * @param localPath local path to the artifact
   * @throws AppCreatorException in case of a failure
   */
  void relink(AppArtifact appArtifact, Path localPath) throws AppCreatorException;

  /**
   * Resolves an artifact.
   *
   * @param artifact artifact to resolve
   * @return local path
   * @throws AppCreatorException in case of a failure
   */
  Path resolve(AppArtifact artifact) throws AppCreatorException;

  /**
   * Collects artifact dependencies.
   *
   * @param artifact root artifact
   * @return collected dependencies
   * @throws AppCreatorException in case of a failure
   */
  List<AppDependency> collectDependencies(AppArtifact artifact) throws AppCreatorException;
}
