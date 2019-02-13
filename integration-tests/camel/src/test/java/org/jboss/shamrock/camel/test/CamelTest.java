package org.jboss.shamrock.camel.test;

import io.restassured.RestAssured;
import org.jboss.shamrock.test.junit.ShamrockTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

@ShamrockTest
public class CamelTest {
    @Test
    public void testRoutes() {
        RestAssured.when().get("/routes").then().body(is("[book1, book2]"));
    }

}
