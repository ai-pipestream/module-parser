package ai.pipestream.module.parser;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

@QuarkusIntegrationTest
class OpenApiEndpointIT {

    @Test
    void testOpenApiEndpointExists() {
        RestAssured.given()
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body(not(emptyString()))
                .body(containsString("openapi"));
    }

    @Test
    void testParserServicePingEndpoint() {
        RestAssured.given()
                .when().get("/modules/parser/api/parser/service/ping")
                .then()
                .statusCode(200)
                .body(is("pong"));
    }
}
