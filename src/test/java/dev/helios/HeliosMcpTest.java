package dev.helios;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HeliosMcpTest {

    @Inject
    ShipmentTools tools;

    @Test
    void catalogExposesAllTools() {
        given().when().get("/api/catalog")
                .then().statusCode(200)
                .body("size()", equalTo(5));
    }

    @Test
    void instanceIsIdentifiedForStatelessRouting() {
        given().when().get("/api/instance")
                .then().statusCode(200)
                .body("instanceId", startsWith("helios-"))
                .body("transport", equalTo("Stateless Streamable HTTP"))
                .body("uptimeMs", greaterThanOrEqualTo(0));
    }

    @Test
    void invokeReturnsResultStampedWithServingInstance() {
        given().contentType("application/json")
                .body("{\"trackingId\":\"HLX-10032291\"}")
                .when().post("/api/invoke/getShipmentStatus")
                .then().statusCode(200)
                .body("tool", equalTo("getShipmentStatus"))
                .body("servedByInstance", startsWith("helios-"))
                .body("result.status", equalTo("IN_TRANSIT"))
                .body("result.destinationHub", equalTo("YYZ"));
    }

    @Test
    void unknownTrackingIdIsRejectedWith400() {
        given().contentType("application/json")
                .body("{\"trackingId\":\"HLX-99999999\"}")
                .when().post("/api/invoke/getShipmentStatus")
                .then().statusCode(400)
                .body("error", startsWith("Unknown tracking ID"));
    }

    @Test
    void toolArgValidationRejectsMalformedTrackingId() {
        // The @Pattern on the MCP tool argument is a real, enforced boundary:
        // Quarkus applies Bean Validation to CDI bean methods automatically.
        assertThrows(ConstraintViolationException.class,
                () -> tools.getShipmentStatus("not-a-tracking-id"));
    }
}
