package dev.helios;

import java.util.List;
import java.util.Map;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Thin REST facade the browser SPA calls to drive the demo.
 *
 * <p>AI agents (Goose, Claude, etc.) talk to this server over the MCP endpoint at
 * {@code /mcp}. The console is a browser app, so for convenience it invokes the same
 * stateless {@link ShipmentService} logic over plain REST — and every response is
 * stamped with the {@link InstanceInfo instance} that served it, so you can watch
 * statelessness in action.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ConsoleResource {

    @Inject
    ShipmentService service;

    @Inject
    InstanceInfo instance;

    public record InstanceSnapshot(String instanceId, long startedAtEpochMs, long uptimeMs, String transport) {
    }

    public record ToolParam(String name, String example, boolean required) {
    }

    public record ToolDescriptor(String name, String title, String description,
                                 List<ToolParam> params, Map<String, Object> sampleArgs) {
    }

    public record InvokeResponse(String tool, String servedByInstance, long latencyMs, Object result) {
    }

    @GET
    @Path("instance")
    public InstanceSnapshot instance() {
        return new InstanceSnapshot(instance.instanceId(), instance.startedAtEpochMs(),
                instance.uptimeMs(), "Stateless Streamable HTTP");
    }

    @GET
    @Path("catalog")
    public List<ToolDescriptor> catalog() {
        return List.of(
                new ToolDescriptor("getShipmentStatus", "Track a Shipment",
                        "Live status, carrier, route, and ETA for a shipment.",
                        List.of(new ToolParam("trackingId", "HLX-10032291", true)),
                        Map.of("trackingId", "HLX-10032291")),
                new ToolDescriptor("getWarehouseInventory", "Check Inventory",
                        "On-hand, reserved, and available stock for a SKU.",
                        List.of(new ToolParam("sku", "SKU-COLD-4521", true)),
                        Map.of("sku", "SKU-COLD-4521")),
                new ToolDescriptor("estimateDelivery", "Estimate a Lane",
                        "Transit days and cost between two hubs for a weight.",
                        List.of(new ToolParam("originHub", "FRA", true),
                                new ToolParam("destinationHub", "YYZ", true),
                                new ToolParam("weightKg", "620", true)),
                        Map.of("originHub", "FRA", "destinationHub", "YYZ", "weightKg", 620)),
                new ToolDescriptor("getCarrierSla", "Carrier SLA",
                        "On-time %, transit days, and damage rate for a carrier.",
                        List.of(new ToolParam("carrierId", "HELIOS-AIR", true)),
                        Map.of("carrierId", "HELIOS-AIR")),
                new ToolDescriptor("listOpenExceptions", "Open Exceptions",
                        "Shipments needing operator attention in a region.",
                        List.of(new ToolParam("region", "APAC", true)),
                        Map.of("region", "APAC")));
    }

    @POST
    @Path("invoke/{tool}")
    public InvokeResponse invoke(@PathParam("tool") String tool, Map<String, Object> args) {
        long start = System.nanoTime();
        Object result = dispatch(tool, args == null ? Map.of() : args);
        long latencyMs = Math.round((System.nanoTime() - start) / 1_000_000.0);
        return new InvokeResponse(tool, instance.instanceId(), latencyMs, result);
    }

    private Object dispatch(String tool, Map<String, Object> args) {
        return switch (tool) {
            case "getShipmentStatus" -> service.getShipmentStatus(str(args, "trackingId"));
            case "getWarehouseInventory" -> service.getWarehouseInventory(str(args, "sku"));
            case "estimateDelivery" -> service.estimateDelivery(
                    str(args, "originHub"), str(args, "destinationHub"), dbl(args, "weightKg"));
            case "getCarrierSla" -> service.getCarrierSla(str(args, "carrierId"));
            case "listOpenExceptions" -> service.listOpenExceptions(str(args, "region"));
            default -> throw new IllegalArgumentException("Unknown tool: " + tool);
        };
    }

    private static String str(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value.toString();
    }

    private static double dbl(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Argument '" + key + "' must be a number");
        }
    }

    @ServerExceptionMapper
    public RestResponse<Map<String, String>> mapBadRequest(IllegalArgumentException e) {
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, Map.of("error", e.getMessage()));
    }
}
