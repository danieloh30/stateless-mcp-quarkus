package dev.helios.agent.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.util.Map;

class AgentServiceTest {
    private AgentService service(Object outcome) {
        var service = new AgentService();
        service.mapper = new ObjectMapper();
        service.fleet =
                (McpClient)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {McpClient.class},
                                (proxy, method, args) -> {
                                    if (!method.getName().equals("executeTool"))
                                        throw new AssertionError(
                                                "Unexpected MCP operation: " + method.getName());
                                    if (outcome instanceof RuntimeException failure) throw failure;
                                    return outcome;
                                });
        return service;
    }

    @Test
    void keepsBusinessDataSeparateFromTheSameCallEvidence() {
        var raw =
                ToolExecutionResult.builder()
                        .resultText("{\"status\":\"IN_TRANSIT\"}")
                        .isError(false)
                        .attributes(
                                Map.of("dev.helios/evidence", Map.of("instanceId", "replica-A")))
                        .build();
        var call = service(raw).invoke("getShipmentStatus", Map.of("trackingId", "HLX-10032291"));
        assertEquals("replica-A", call.evidence().get("instanceId"));
        assertEquals("{\"status\":\"IN_TRANSIT\"}", call.result().toString());
        assertFalse(call.isError());
    }

    @Test
    void preservesSdkWrappedToolErrorsWithoutCallingThemDependencyFailures() {
        var call =
                service(new ToolExecutionException("Invalid tracking ID"))
                        .invoke("getShipmentStatus", Map.of());
        assertTrue(call.isError());
        assertEquals("mcp_tool_error", ((Map<?, ?>) call.result()).get("type"));
        assertTrue(
                call.evidence()
                        .isEmpty()); // Do not invent a serving replica for a stripped SDK error.
    }

    @Test
    void transportFailuresRemainFailures() {
        var failure = new ToolExecutionException(new ConnectException("connection refused"));
        assertThrows(
                ToolExecutionException.class,
                () -> service(failure).invoke("getShipmentStatus", Map.of()));
    }

    @Test
    void bridgeKeepsEvidenceInAttributesOutsideTheTextSentToTheModel() {
        var raw =
                ToolExecutionResult.builder()
                        .resultText("{\"status\":\"IN_TRANSIT\"}")
                        .isError(false)
                        .attributes(
                                Map.of("dev.helios/evidence", Map.of("instanceId", "replica-A")))
                        .build();
        var bridge =
                service(raw).invokeText("getShipmentStatus", Map.of("trackingId", "HLX-10032291"));
        assertFalse(bridge.resultText().contains("replica-A"));
        var execution =
                dev.langchain4j.service.tool.ToolExecution.builder()
                        .request(
                                dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                        .name("getShipmentStatus")
                                        .arguments("{}")
                                        .build())
                        .invocationContext(
                                dev.langchain4j.invocation.InvocationContext.builder().build())
                        .result(bridge)
                        .build();
        assertInstanceOf(
                dev.helios.agent.dto.InvokeResult.class,
                execution.attributes().get("dev.helios/call"));
    }
}
