package dev.helios.tools;

import dev.helios.service.InstanceInfo;

import io.quarkiverse.mcp.server.*;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Demo-only, allowlisted evidence from the very request executing the tool. */
@ApplicationScoped
@GlobalGuardrail
public class DemoEvidence implements ToolInputGuardrail, ToolOutputGuardrail {
    @Inject InstanceInfo instance;
    @Inject RoutingContext routing;

    @Override
    public void apply(ToolInputContext ctx) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : new String[] {"MCP-Protocol-Version", "Mcp-Method", "Mcp-Name"}) {
            String value = routing.request().getHeader(name);
            if (value != null) headers.put(name, value);
        }
        var params = new LinkedHashMap<String, Object>();
        params.put("name", ctx.getTool().name());
        params.put("arguments", ctx.getArguments().getMap());
        var metadata = new LinkedHashMap<String, Object>();
        var actual = ctx.getMeta().asJsonObject();
        for (String key :
                new String[] {
                    "io.modelcontextprotocol/protocolVersion",
                    "io.modelcontextprotocol/clientInfo",
                    "io.modelcontextprotocol/clientCapabilities"
                }) if (actual.containsKey(key)) metadata.put(key, actual.getValue(key));
        params.put("_meta", metadata);
        routing.put(
                "helios.evidence",
                Map.of(
                        "instanceId",
                        instance.instanceId(),
                        "uptimeMs",
                        instance.uptimeMs(),
                        "transientConnection",
                        ctx.getConnection().isTransient(),
                        "requestHeaders",
                        headers,
                        "request",
                        Map.of(
                                "jsonrpc",
                                "2.0",
                                "id",
                                ctx.getRequestId().value(),
                                "method",
                                "tools/call",
                                "params",
                                params)));
    }

    @Override
    public void apply(ToolOutputContext ctx) {
        Map<String, Object> evidence = routing.get("helios.evidence");
        if (evidence == null) return;
        var response = ctx.getResponse();
        var meta = new LinkedHashMap<MetaKey, Object>();
        if (response._meta() != null) meta.putAll(response._meta());
        meta.put(MetaKey.from("dev.helios/evidence"), evidence);
        ctx.setResponse(
                new ToolResponse(
                        response.isError(),
                        response.content(),
                        response.structuredContent(),
                        meta));
    }
}
