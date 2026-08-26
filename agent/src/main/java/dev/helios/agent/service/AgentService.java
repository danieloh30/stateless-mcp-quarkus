package dev.helios.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The agent tier's MCP client.
 *
 * <p>This is the "MCP client" box in the architecture: the browser console talks
 * to this agent over REST, and the agent uses a managed {@link McpClient} to reach
 * the stateless MCP server fleet through the load balancer. Because the servers are
 * stateless, each {@code executeTool} call can be served by a different replica.
 */
@ApplicationScoped
public class AgentService {

    @Inject
    @McpClientName("fleet")
    McpClient fleet;

    @Inject
    ObjectMapper mapper;

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
            out.add(Map.of(
                    "name", tool.name(),
                    "description", tool.description() == null ? "" : tool.description(),
                    "params", params));
        }
        return out;
    }

    /** Invoke a tool on the fleet and return the parsed JSON result. */
    public Object invoke(String tool, Map<String, Object> args) {
        String text = exec(tool, toJson(args == null ? Map.of() : args));
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * Invoke a fleet tool and return the raw MCP result text. Used by the agentic
     * tool bridges: an LLM tool wants the plain string (validation errors included),
     * not a parsed tree.
     */
    public String invokeText(String tool, Map<String, Object> args) {
        return exec(tool, toJson(args == null ? Map.of() : args));
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
        return fleet.executeTool(ToolExecutionRequest.builder()
                .name(tool)
                .arguments(argsJson)
                .build()).resultText();
    }

    private String toJson(Map<String, Object> args) {
        try {
            return mapper.writeValueAsString(args);
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize arguments", e);
        }
    }
}
