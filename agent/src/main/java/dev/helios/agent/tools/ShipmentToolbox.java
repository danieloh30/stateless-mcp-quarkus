package dev.helios.agent.tools;

import java.util.Map;

import dev.helios.agent.service.AgentService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Shipment-domain tools, bridged from the stateless MCP fleet.
 *
 * <p>{@code @McpToolBox("fleet")} can expose the entire remote catalog directly to a
 * declarative agent. This bridge intentionally re-exposes only the shipment subset as
 * LangChain4j {@link Tool tools} and is attached with {@code @ToolBox}; the inventory
 * and exception agents receive their own least-privilege subsets. Every call still
 * goes through the managed {@code McpClient} to a stateless replica.
 */
@ApplicationScoped
public class ShipmentToolbox {

    @Inject
    AgentService fleet;

    @Tool("Look up the live status, carrier, route, and ETA for a Helios shipment by its tracking ID.")
    public String getShipmentStatus(
            @P("Tracking ID formatted as HLX-XXXXXXXX, e.g. HLX-10032291") String trackingId) {
        return fleet.invokeText("getShipmentStatus", Map.of("trackingId", trackingId));
    }

    @Tool("Estimate transit days and cost for a lane between two Helios hubs for a given shipment weight.")
    public String estimateDelivery(
            @P("Origin hub IATA code, e.g. FRA, SIN, YYZ, AMS, JFK") String originHub,
            @P("Destination hub IATA code, e.g. FRA, SIN, YYZ, AMS, JFK") String destinationHub,
            @P("Chargeable weight in kilograms") double weightKg) {
        return fleet.invokeText("estimateDelivery",
                Map.of("originHub", originHub, "destinationHub", destinationHub, "weightKg", weightKg));
    }

    @Tool("Get current-quarter service-level metrics (on-time %, transit days, damage rate) for a carrier.")
    public String getCarrierSla(
            @P("Carrier ID, e.g. HELIOS-AIR, HELIOS-GROUND, PARTNER-DHL, PARTNER-FEDEX") String carrierId) {
        return fleet.invokeText("getCarrierSla", Map.of("carrierId", carrierId));
    }
}
