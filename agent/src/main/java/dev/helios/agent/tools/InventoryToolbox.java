package dev.helios.agent.tools;

import dev.helios.agent.service.AgentService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolExecutionResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Warehouse-inventory tools, bridged from the stateless MCP fleet. See {@link ShipmentToolbox} for
 * why the fleet tools are re-exposed as langchain4j tools.
 */
@ApplicationScoped
public class InventoryToolbox {

    @Inject AgentService fleet;

    @Tool(
            "Retrieve the on-hand, reserved, and available stock for a SKU in the Helios warehouse"
                + " network.")
    public ToolExecutionResult getWarehouseInventory(
            @P("Stock keeping unit, e.g. SKU-COLD-4521") String sku) {
        return fleet.invokeText("getWarehouseInventory", Map.of("sku", sku));
    }
}
