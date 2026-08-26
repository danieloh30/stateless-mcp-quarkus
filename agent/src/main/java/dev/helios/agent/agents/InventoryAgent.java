package dev.helios.agent.agents;

import dev.helios.agent.tools.InventoryToolbox;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import io.quarkiverse.langchain4j.ToolBox;

/**
 * Sub-agent for warehouse inventory questions. See {@link ShipmentAgent} for the pattern.
 */
public interface InventoryAgent {

    @SystemMessage("""
            You are a Helios Logistics warehouse inventory specialist. Answer questions about
            on-hand, reserved, and available stock for a SKU. Always call the inventory tool to
            fetch live numbers — never guess. If the SKU is invalid or missing, say so plainly.
            """)
    @UserMessage("{{request}}")
    @Agent(description = "Handles warehouse inventory / stock-level questions for a SKU",
            outputKey = "inventoryAnswer")
    @ToolBox(InventoryToolbox.class)
    String handle(@V("request") String request);
}
