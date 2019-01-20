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

package org.jboss.shamrock.undertow.runtime;

import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shamrock.runtime.ConfigGroup;

@ConfigGroup
public class HttpConfig {

  /** The HTTP port */
  @ConfigProperty(name = "port", defaultValue = "8080")
  public Integer port;

  /** The HTTP host */
  @ConfigProperty(name = "host", defaultValue = "localhost")
  public String host;

  /**
   * The number of worker threads used for blocking tasks, this will be automatically set to a
   * reasonable value based on the number of CPU core if it is not provided
   */
  @ConfigProperty(name = "workerThreads")
  public Optional<Integer> workerThreads;

  /**
   * The number if IO threads used to perform IO. This will be automatically set to a reasonable
   * value based on the number of CPU cores if it is not provided
   */
  @ConfigProperty(name = "ioThreads")
  public Optional<Integer> ioThreads;
}
