package dev.helios.model;

/** Identity of the MCP server instance that handled a request. */
public record ServerInstance(
        String instanceId,
        long uptimeMs,
        String transport
) {
}
