package dev.helios.agent.tools;

import java.util.Map;

import dev.helios.agent.service.AgentService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Open-exception tools, bridged from the stateless MCP fleet.
 * See {@link ShipmentToolbox} for why the fleet tools are re-exposed as langchain4j tools.
 */
@ApplicationScoped
public class ExceptionToolbox {

    @Inject
    AgentService fleet;

    @Tool("List open shipment exceptions requiring operator attention in a region (EU, APAC, or NA).")
    public String listOpenExceptions(
            @P("Region code: EU, APAC, or NA") String region) {
        return fleet.invokeText("listOpenExceptions", Map.of("region", region));
    }
}
