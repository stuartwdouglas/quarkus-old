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

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import org.jboss.shamrock.runtime.InjectionInstance;

public class ShamrockInstanceFactory<T> implements InstanceFactory<T> {

  private final InjectionInstance<T> injectionInstance;

  public ShamrockInstanceFactory(InjectionInstance<T> injectionInstance) {
    this.injectionInstance = injectionInstance;
  }

  @Override
  public InstanceHandle<T> createInstance() throws InstantiationException {
    return new InstanceHandle<T>() {
      @Override
      public T getInstance() {
        return injectionInstance.newInstance();
      }

      @Override
      public void release() {}
    };
  }
}
