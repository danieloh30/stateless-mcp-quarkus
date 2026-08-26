package dev.helios;

import java.util.List;
import java.util.function.Supplier;

import dev.helios.model.CarrierSla;
import dev.helios.model.DeliveryEstimate;
import dev.helios.model.InventoryItem;
import dev.helios.model.ServerInstance;
import dev.helios.model.ShipmentException;
import dev.helios.model.ShipmentStatus;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Helios Logistics MCP tool surface.
 *
 * <p>Each {@code @Tool} method is a lightweight, stateless request handler: validate,
 * run one lookup, return a structured record. Because no state is carried between
 * calls, {@code quarkus-mcp-server-http} exposes these over stateless Streamable HTTP —
 * every request is independent, so the server load-balances trivially and scales to
 * zero when idle.
 *
 * <p>The {@link ShipmentService} enforces the input/lookup rules and throws
 * {@link IllegalArgumentException} with a clear reason. {@link #guard(Supplier)} turns
 * that into a {@link ToolCallException} so the reason reaches the MCP client instead of
 * a generic "internal error".
 */
@ApplicationScoped
public class ShipmentTools {

    @Inject
    ShipmentService service;

    @Inject
    InstanceInfo instance;

    @Tool(description = "Return the identity of the MCP server instance handling this request "
            + "(instance ID, uptime, transport). Demonstrates stateless load balancing: with "
            + "multiple replicas the instance ID rotates while results stay identical.")
    public ServerInstance getServerInstance() {
        return new ServerInstance(instance.instanceId(), instance.uptimeMs(), "Stateless Streamable HTTP");
    }

    @Tool(description = "Look up the live status, carrier, route, and ETA for a Helios shipment by its tracking ID.")
    public ShipmentStatus getShipmentStatus(
            @ToolArg(description = "Tracking ID formatted as HLX-XXXXXXXX, e.g. HLX-10032291")
            String trackingId) {
        return guard(() -> service.getShipmentStatus(trackingId));
    }

    @Tool(description = "Retrieve the on-hand, reserved, and available stock for a SKU in the Helios warehouse network.")
    public InventoryItem getWarehouseInventory(
            @ToolArg(description = "Stock keeping unit, e.g. SKU-COLD-4521")
            String sku) {
        return guard(() -> service.getWarehouseInventory(sku));
    }

    @Tool(description = "Estimate transit days and cost for a lane between two Helios hubs for a given shipment weight.")
    public DeliveryEstimate estimateDelivery(
            @ToolArg(description = "Origin hub IATA code, e.g. FRA, SIN, YYZ, AMS, JFK")
            String originHub,
            @ToolArg(description = "Destination hub IATA code, e.g. FRA, SIN, YYZ, AMS, JFK")
            String destinationHub,
            @ToolArg(description = "Chargeable weight in kilograms")
            double weightKg) {
        return guard(() -> service.estimateDelivery(originHub, destinationHub, weightKg));
    }

    @Tool(description = "Get current-quarter service-level metrics (on-time %, transit days, damage rate) for a carrier.")
    public CarrierSla getCarrierSla(
            @ToolArg(description = "Carrier ID, e.g. HELIOS-AIR, HELIOS-GROUND, PARTNER-DHL, PARTNER-FEDEX")
            String carrierId) {
        return guard(() -> service.getCarrierSla(carrierId));
    }

    @Tool(description = "List open shipment exceptions requiring operator attention in a region (EU, APAC, or NA).")
    public List<ShipmentException> listOpenExceptions(
            @ToolArg(description = "Region code: EU, APAC, or NA")
            String region) {
        return guard(() -> service.listOpenExceptions(region));
    }

    /** Run a lookup, surfacing validation/not-found reasons to the MCP client. */
    private <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (IllegalArgumentException e) {
            throw new ToolCallException(e.getMessage());
        }
    }
}
