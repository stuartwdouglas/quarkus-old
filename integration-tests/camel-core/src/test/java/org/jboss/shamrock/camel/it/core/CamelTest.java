package org.jboss.shamrock.camel.it.core;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.net.URI;

import org.jboss.shamrock.test.junit.ShamrockTest;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

@ShamrockTest
public class CamelTest {
    @Test
    public void testRoutes() {
        RestAssured.when().get("/test/routes").then().body(containsString("timer"));
    }

    @Test
    public void testProperties() {
        RestAssured.when().get("/test/property/camel.context.name").then().body(is("shamrock-camel-example"));
    }

    @Test
    public void testNetty4Http() throws Exception {
        RestAssured.when().get(new URI("http://localhost:8999/foo")).then().body(is("Netty Hello World"));
    }
}
