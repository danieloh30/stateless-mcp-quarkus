package dev.helios;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Exercises the server over the real MCP endpoint (/mcp), the same JSON-RPC the
 * console and AI agents use. No initialize/session is needed — each request is
 * independent, which is what makes the server horizontally scalable.
 *
 * <p>Assertions match on the raw response body so they hold whether the server
 * frames the reply as plain JSON or as Server-Sent Events.
 */
@QuarkusTest
class HeliosMcpTest {

    private static final String MCP = "/mcp";
    private static final String ACCEPT = "application/json, text/event-stream";

    @Inject
    ShipmentTools tools;

    private String post(String body) {
        return given().contentType("application/json").header("Accept", ACCEPT)
                .body(body).when().post(MCP)
                .then().statusCode(200)
                .extract().asString();
    }

    @Test
    void toolsListWorksWithoutASession() {
        String body = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        assertThat(body, containsString("getShipmentStatus"));
        assertThat(body, containsString("getServerInstance"));
        assertThat(body, containsString("listOpenExceptions"));
    }

    @Test
    void toolsCallReturnsResultWithoutASession() {
        String body = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"getShipmentStatus\",\"arguments\":{\"trackingId\":\"HLX-10032291\"}}}");
        assertThat(body, containsString("IN_TRANSIT"));
        assertThat(body, containsString("YYZ"));
    }

    @Test
    void serverInstanceToolReportsIdentity() {
        String body = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"getServerInstance\",\"arguments\":{}}}");
        assertThat(body, containsString("helios-"));
        assertThat(body, containsString("Stateless Streamable HTTP"));
    }

    @Test
    void malformedArgumentReturnsAFriendlyReason() {
        // Validation is a real server-side boundary, and the reason reaches the client
        // (not a generic "internal error").
        String body = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"getShipmentStatus\",\"arguments\":{\"trackingId\":\"BAD\"}}}");
        assertThat(body, containsString("HLX-XXXXXXXX"));
    }

    @Test
    void invalidInputRaisesToolCallExceptionWithReason() {
        ToolCallException ex = assertThrows(ToolCallException.class,
                () -> tools.getShipmentStatus("not-a-tracking-id"));
        assertThat(ex.getMessage(), containsString("HLX-XXXXXXXX"));
    }
}
