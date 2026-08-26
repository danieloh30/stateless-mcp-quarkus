package dev.helios.agent.agents;

import dev.helios.agent.tools.ShipmentToolbox;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import io.quarkiverse.langchain4j.ToolBox;

/**
 * Sub-agent for shipment tracking, lane delivery estimates, and carrier SLAs.
 *
 * <p>A declarative agent: the interface method carries {@code @Agent}, and Quarkus
 * auto-registers it as an {@code @ApplicationScoped} CDI bean backed by the configured
 * OpenAI chat model. {@code @ToolBox} gives it the shipment slice of the MCP fleet.
 */
public interface ShipmentAgent {

    @SystemMessage("""
            You are a Helios Logistics shipment specialist. Answer questions about shipment
            tracking, transit/cost estimates between hubs, and carrier service levels.
            Always call a tool to fetch live data — never invent tracking numbers, ETAs, or
            metrics. If a tool reports a validation error or no result, say so plainly.
            """)
    @UserMessage("{{request}}")
    @Agent(description = "Handles shipment tracking, delivery/lane estimates, and carrier SLA questions",
            outputKey = "shipmentAnswer")
    @ToolBox(ShipmentToolbox.class)
    String handle(@V("request") String request);
}
