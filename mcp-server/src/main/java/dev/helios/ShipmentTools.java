package dev.helios;

import java.util.List;

import dev.helios.model.CarrierSla;
import dev.helios.model.DeliveryEstimate;
import dev.helios.model.InventoryItem;
import dev.helios.model.ServerInstance;
import dev.helios.model.ShipmentException;
import dev.helios.model.ShipmentStatus;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The Helios Logistics MCP tool surface.
 *
 * <p>Each {@code @Tool} method is a lightweight, stateless request handler:
 * validate the arguments, run one lookup, return a structured record. Because no
 * state is carried between calls, the {@code quarkus-mcp-server-http} extension can
 * expose these over stateless Streamable HTTP — every request is independent, so
 * the server load-balances trivially and can scale to zero when idle.
 *
 * <p>Return types are plain records: Quarkus serializes them to MCP JSON with no
 * mapping code. (A tool would return a Mutiny {@code Uni<T>} only when it needs
 * genuine non-blocking I/O; these lookups are synchronous.)
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
            @ToolArg(description = "Tracking ID formatted as HLX-XXXXXXXX")
            @Pattern(regexp = "^HLX-[0-9]{8}$", message = "tracking ID must match HLX-XXXXXXXX")
            String trackingId) {
        return service.getShipmentStatus(trackingId);
    }

    @Tool(description = "Retrieve the on-hand, reserved, and available stock for a SKU in the Helios warehouse network.")
    public InventoryItem getWarehouseInventory(
            @ToolArg(description = "Stock keeping unit, e.g. SKU-COLD-4521")
            @NotBlank
            @Size(max = 32)
            String sku) {
        return service.getWarehouseInventory(sku);
    }

    @Tool(description = "Estimate transit days and cost for a lane between two Helios hubs for a given shipment weight.")
    public DeliveryEstimate estimateDelivery(
            @ToolArg(description = "Origin hub IATA code, e.g. FRA, SIN, YYZ, AMS, JFK")
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "origin hub must be a 3-letter code")
            String originHub,
            @ToolArg(description = "Destination hub IATA code, e.g. FRA, SIN, YYZ, AMS, JFK")
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "destination hub must be a 3-letter code")
            String destinationHub,
            @ToolArg(description = "Chargeable weight in kilograms")
            @Positive(message = "weightKg must be greater than zero")
            double weightKg) {
        return service.estimateDelivery(originHub, destinationHub, weightKg);
    }

    @Tool(description = "Get current-quarter service-level metrics (on-time %, transit days, damage rate) for a carrier.")
    public CarrierSla getCarrierSla(
            @ToolArg(description = "Carrier ID, e.g. HELIOS-AIR, HELIOS-GROUND, PARTNER-DHL, PARTNER-FEDEX")
            @NotBlank
            @Size(max = 40)
            String carrierId) {
        return service.getCarrierSla(carrierId);
    }

    @Tool(description = "List open shipment exceptions requiring operator attention in a region (EU, APAC, or NA).")
    public List<ShipmentException> listOpenExceptions(
            @ToolArg(description = "Region code: EU, APAC, or NA")
            @Pattern(regexp = "^(?i)(EU|APAC|NA)$", message = "region must be EU, APAC, or NA")
            String region) {
        return service.listOpenExceptions(region);
    }
}
