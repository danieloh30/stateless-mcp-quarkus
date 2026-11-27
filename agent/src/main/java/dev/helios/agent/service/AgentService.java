package dev.helios.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;

import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The agent tier's MCP client.
 *
 * <p>This is the "MCP client" box in the architecture: the browser console talks to this agent over
 * REST, and the agent uses a managed {@link McpClient} to reach the stateless MCP server fleet
 * through the load balancer. Because the servers are stateless, each {@code executeTool} call can
 * be served by a different replica.
 */
@ApplicationScoped
public class AgentService {

    @Inject
    @McpClientName("fleet")
    McpClient fleet;

    @Inject ObjectMapper mapper;

    /** Live tool catalog, discovered from the fleet via MCP {@code tools/list}. */
    public List<Map<String, Object>> catalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolSpecification tool : fleet.listTools()) {
            if ("getServerInstance".equals(tool.name())) {
                continue; // internal demo tool, surfaced via /agent/instance
            }
            List<String> params = new ArrayList<>();
            if (tool.parameters() != null && tool.parameters().properties() != null) {
                params.addAll(tool.parameters().properties().keySet());
            }
            out.add(
                    Map.of(
                            "name",
                            tool.name(),
                            "description",
                            tool.description() == null ? "" : tool.description(),
                            "params",
                            params));
        }
        return out;
    }

    @Inject DemoRuns runs;

    /** One business invocation, with evidence attached to that same MCP result. */
    public dev.helios.agent.dto.InvokeResult invoke(String tool, Map<String, Object> args) {
        if (!java.util.Set.of(
                        "getShipmentStatus",
                        "getWarehouseInventory",
                        "estimateDelivery",
                        "getCarrierSla",
                        "listOpenExceptions")
                .contains(tool)) {
            throw new IllegalArgumentException("Unknown business tool: " + tool);
        }
        long started = System.nanoTime();
        dev.langchain4j.service.tool.ToolExecutionResult response;
        try {
            response =
                    fleet.executeTool(
                            ToolExecutionRequest.builder()
                                    .name(tool)
                                    .arguments(toJson(args == null ? Map.of() : args))
                                    .build());
        } catch (dev.langchain4j.exception.ToolExecutionException e) {
            // The SDK wraps returned MCP tool errors in a plain RuntimeException.
            // Network failures have typed transport causes and remain 502/504 errors.
            Throwable cause = e.getCause();
            if (cause == null
                    || cause.getClass() != RuntimeException.class
                    || cause.getCause() != null
                    || Integer.valueOf(-32603).equals(e.errorCode())) throw e;
            var problem = new java.util.LinkedHashMap<String, Object>();
            problem.put("message", cause.getMessage());
            problem.put("code", e.errorCode());
            problem.put("type", "mcp_tool_error");
            return new dev.helios.agent.dto.InvokeResult(
                    tool, problem, true, (System.nanoTime() - started) / 1_000_000, Map.of());
        }
        Object result;
        try {
            result = mapper.readTree(response.resultText());
        } catch (Exception e) {
            result = response.resultText();
        }
        Object raw = response.attributes().get("dev.helios/evidence");
        Map<String, Object> evidence =
                raw instanceof Map<?, ?> ? mapper.convertValue(raw, Map.class) : Map.of();
        return new dev.helios.agent.dto.InvokeResult(
                tool,
                result,
                response.isError(),
                (System.nanoTime() - started) / 1_000_000,
                evidence);
    }

    public dev.langchain4j.service.tool.ToolExecutionResult invokeText(
            String tool, Map<String, Object> args) {
        var call = invoke(tool, args);
        return dev.langchain4j.service.tool.ToolExecutionResult.builder()
                .resultText(toJson(Map.of("isError", call.isError(), "result", call.result())))
                .isError(call.isError())
                .attributes(Map.of("dev.helios/call", call))
                .build();
    }

    /** Read only this question's actual monitored tool executions. */
    public void recordTrace(
            dev.langchain4j.agentic.observability.AgentInvocation invocation, String runId) {
        for (var execution : invocation.toolExecutions()) {
            Object value = execution.attributes().get("dev.helios/call");
            if (value instanceof dev.helios.agent.dto.InvokeResult call)
                runs.record(runId, invocation.agent().name(), call);
        }
        for (var nested : invocation.nestedInvocations()) recordTrace(nested, runId);
    }

    /** Ask the serving replica who it is (MCP {@code getServerInstance} tool). */
    public Object serverInstance() {
        String text = exec("getServerInstance", "{}");
        try {
            return mapper.readValue(text, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected getServerInstance payload: " + text, e);
        }
    }

    private String exec(String tool, String argsJson) {
        return fleet.executeTool(
                        ToolExecutionRequest.builder().name(tool).arguments(argsJson).build())
                .resultText();
    }

    private String toJson(Map<String, Object> args) {
        try {
            return mapper.writeValueAsString(args);
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize arguments", e);
        }
    }
}
