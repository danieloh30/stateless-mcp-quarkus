package dev.helios.agent.dto;

/** Result of a direct MCP tool invocation from the console's raw "Run 5×" view. */
public record InvokeResult(String tool, Object result) {
}
