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

package org.jboss.shamrock.transactions.runtime;

import java.lang.reflect.Field;
import java.util.Properties;

import org.jboss.logging.Logger;
import org.jboss.shamrock.runtime.annotations.Template;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.coordinator.TxControl;
import com.arjuna.common.util.propertyservice.PropertiesFactory;

@Template
public class TransactionTemplate {

    private static Properties defaultProperties;

    private static final Logger log = Logger.getLogger(TransactionTemplate.class);

    public void setNodeName(String name) {

        try {
            arjPropertyManager.getCoreEnvironmentBean().setNodeIdentifier(name);
            TxControl.setXANodeName("shamrock");
        } catch (CoreEnvironmentBeanException e) {
            e.printStackTrace();
        }
    }

    public void setDefaultProperties(Properties properties) {
        //TODO: this is a huge hack to avoid loading XML parsers
        //this needs a proper SPI
        try {
            Field field = PropertiesFactory.class.getDeclaredField("delegatePropertiesFactory");
            field.setAccessible(true);
            field.set(null, new ShamrockPropertiesFactory(properties));

        } catch (Exception e) {
            log.error("Could not override transaction properties factory", e);
        }

        defaultProperties = properties;
    }

    public static Properties getDefaultProperties() {
        return defaultProperties;
    }
}
