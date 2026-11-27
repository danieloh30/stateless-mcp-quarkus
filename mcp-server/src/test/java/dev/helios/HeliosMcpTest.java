package dev.helios;

import static io.restassured.RestAssured.given;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

import org.junit.jupiter.api.Test;

@QuarkusTest
class HeliosMcpTest {
    private String body(String method, String tool, String args) {
        var params =
                new JsonObject()
                        .put(
                                "_meta",
                                new JsonObject()
                                        .put(
                                                "io.modelcontextprotocol/protocolVersion",
                                                "2026-07-28")
                                        .put(
                                                "io.modelcontextprotocol/clientCapabilities",
                                                new JsonObject())
                                        .put(
                                                "io.modelcontextprotocol/clientInfo",
                                                new JsonObject()
                                                        .put("name", "helios-test")
                                                        .put("version", "1")));
        if (tool != null) params.put("name", tool).put("arguments", new JsonObject(args));
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", 17)
                .put("method", method)
                .put("params", params)
                .encode();
    }

    private Response post(String method, String tool, String args) {
        var req =
                given().contentType("application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .header("MCP-Protocol-Version", "2026-07-28")
                        .header("Mcp-Method", method);
        if (tool != null) req.header("Mcp-Name", tool);
        return req.body(body(method, tool, args)).post("/mcp");
    }

    private JsonObject envelope(Response response) {
        String value = response.asString();
        if (value.startsWith("{")) return new JsonObject(value);
        return value.lines()
                .filter(l -> l.startsWith("data:"))
                .map(l -> new JsonObject(l.substring(5).trim()))
                .filter(j -> j.containsKey("result") || j.containsKey("error"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void discoveryAndCatalogUseModernProtocolWithoutSession() {
        var discovery = post("server/discover", null, null);
        assertEquals(200, discovery.statusCode());
        assertNull(discovery.header("Mcp-Session-Id"));
        var list = post("tools/list", null, null);
        assertEquals(200, list.statusCode());
        assertTrue(list.asString().contains("getShipmentStatus"));
        assertEquals("complete", envelope(list).getJsonObject("result").getString("resultType"));
    }

    @Test
    void sameCallContainsReplicaAndObservedProtocolEvidence() {
        var response = post("tools/call", "getShipmentStatus", "{\"trackingId\":\"HLX-10032291\"}");
        assertEquals(200, response.statusCode());
        assertNull(response.header("Mcp-Session-Id"));
        var result = envelope(response).getJsonObject("result");
        assertTrue(result.encode().contains("IN_TRANSIT"));
        var e = result.getJsonObject("_meta").getJsonObject("dev.helios/evidence");
        assertTrue(e.getBoolean("transientConnection"));
        assertTrue(e.getString("instanceId").startsWith("helios-"));
        assertEquals(
                "2026-07-28", e.getJsonObject("requestHeaders").getString("MCP-Protocol-Version"));
        assertEquals(
                "getShipmentStatus",
                e.getJsonObject("request").getJsonObject("params").getString("name"));
        assertFalse(e.encode().contains("Authorization"));
    }

    @Test
    void invalidInputReturnsStructuredToolError() {
        var response = post("tools/call", "getShipmentStatus", "{\"trackingId\":\"BAD\"}");
        assertEquals(200, response.statusCode());
        assertTrue(envelope(response).getJsonObject("result").getBoolean("isError"));
        assertTrue(response.asString().contains("HLX-XXXXXXXX"));
    }

    @Test
    void mismatchedNameHeaderIsRejectedBeforeToolExecution() {
        var response =
                given().contentType("application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .header("MCP-Protocol-Version", "2026-07-28")
                        .header("Mcp-Method", "tools/call")
                        .header("Mcp-Name", "getCarrierSla")
                        .body(
                                body(
                                        "tools/call",
                                        "getShipmentStatus",
                                        "{\"trackingId\":\"HLX-10032291\"}"))
                        .post("/mcp");
        assertEquals(400, response.statusCode());
        assertEquals(-32020, envelope(response).getJsonObject("error").getInteger("code"));
    }

    @Test
    void missingVersionHeaderIsRejected() {
        var response =
                given().contentType("application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .header("Mcp-Method", "tools/list")
                        .body(body("tools/list", null, null))
                        .post("/mcp");
        assertEquals(400, response.statusCode());
        assertEquals(-32020, envelope(response).getJsonObject("error").getInteger("code"));
    }
}
