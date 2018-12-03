/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.shamrock.restclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.MalformedURLException;
import java.net.URL;

import javax.ws.rs.client.Client;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.protean.arc.Arc;
import org.jboss.shamrock.restclient.runtime.RestClientProxy;
import org.jboss.shamrock.test.Deployment;
import org.jboss.shamrock.test.ShamrockUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Martin Kouba
 */
@RunWith(ShamrockUnitTest.class)
public class RestClientProxyTest {

    @Deployment
    public static JavaArchive createTestArchive() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClasses(HelloClient.class, HelloResource.class, JaxRsActivator.class, Counter.class);
    }

    private static URL baseUrl() throws MalformedURLException {
        return new URL("http://" + ConfigProvider.getConfig()
                .getValue("shamrock.http.host", String.class) + ":"
                + ConfigProvider.getConfig()
                        .getValue("shamrock.http.port", String.class));
    }

    @Test
    public void testGetClient() throws InterruptedException, IllegalStateException, RestClientDefinitionException, MalformedURLException {
        Counter counter = Arc.container()
                .instance(Counter.class)
                .get();
        counter.reset(1);

        HelloClient helloClient = RestClientBuilder.newBuilder()
                .baseUrl(baseUrl())
                .build(HelloClient.class);

        Client client = ((RestClientProxy) helloClient).getClient();
        assertNotNull(client);
        assertEquals("C:OK1:C", helloClient.hello());
        client.close();
    }

}