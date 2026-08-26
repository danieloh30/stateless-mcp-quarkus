package dev.helios;

import java.util.List;
import java.util.Map;

import dev.helios.model.CarrierSla;
import dev.helios.model.DeliveryEstimate;
import dev.helios.model.InventoryItem;
import dev.helios.model.ShipmentException;
import dev.helios.model.ShipmentStatus;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Business logic for Helios Logistics tooling.
 *
 * <p>Every method is a pure, self-contained lookup against in-memory reference
 * data — there is no per-caller session, no conversational memory, and no shared
 * mutable state. That is exactly what makes the MCP server in front of it
 * horizontally scalable and safe to scale to zero: any instance can serve any
 * request, in any order.
 */
@ApplicationScoped
public class ShipmentService {

    private static final Map<String, ShipmentStatus> SHIPMENTS = Map.of(
            "HLX-10032291", new ShipmentStatus("HLX-10032291", "IN_TRANSIT", "HELIOS-AIR",
                    "FRA", "YYZ", "2026-08-24T21:10:00Z / departed FRA gateway", "2026-08-27", false),
            "HLX-10044817", new ShipmentStatus("HLX-10044817", "DELIVERED", "PARTNER-FEDEX",
                    "SIN", "JFK", "2026-08-23T14:02:00Z / signed by K. TAN", "2026-08-23", false),
            "HLX-10051120", new ShipmentStatus("HLX-10051120", "EXCEPTION", "PARTNER-DHL",
                    "AMS", "SIN", "2026-08-24T08:45:00Z / held at customs (SIN)", "2026-08-29", true),
            "HLX-10067734", new ShipmentStatus("HLX-10067734", "OUT_FOR_DELIVERY", "HELIOS-GROUND",
                    "YYZ", "YYZ", "2026-08-25T06:30:00Z / on vehicle for delivery", "2026-08-25", false));

    private static final Map<String, InventoryItem> INVENTORY = Map.of(
            "SKU-COLD-4521", inv("SKU-COLD-4521", "Cold-chain vaccine tray (2-8C)", "FRA-DC1", 480, 360, 200),
            "SKU-ELEC-8830", inv("SKU-ELEC-8830", "Edge gateway appliance", "SIN-DC2", 1240, 300, 250),
            "SKU-AUTO-2210", inv("SKU-AUTO-2210", "EV battery module", "YYZ-DC1", 96, 74, 40));

    private static final Map<String, CarrierSla> CARRIERS = Map.of(
            "HELIOS-AIR", new CarrierSla("HELIOS-AIR", "Helios Air Freight", 98.6, 2.1, 0.12, "PLATINUM", "2026-Q3"),
            "HELIOS-GROUND", new CarrierSla("HELIOS-GROUND", "Helios Ground Network", 96.2, 3.4, 0.28, "GOLD", "2026-Q3"),
            "PARTNER-DHL", new CarrierSla("PARTNER-DHL", "DHL (partner lane)", 94.8, 3.9, 0.41, "SILVER", "2026-Q3"),
            "PARTNER-FEDEX", new CarrierSla("PARTNER-FEDEX", "FedEx (partner lane)", 95.5, 3.6, 0.35, "GOLD", "2026-Q3"));

    private static final List<ShipmentException> EXCEPTIONS = List.of(
            new ShipmentException("HLX-10051120", "APAC", "CUSTOMS_HOLD", "HIGH",
                    "2026-08-24T08:45:00Z", "Submit commercial invoice to SIN customs broker"),
            new ShipmentException("HLX-10098450", "APAC", "WEATHER_DELAY", "MEDIUM",
                    "2026-08-24T22:10:00Z", "Re-route via KUL hub; notify consignee of +1 day ETA"),
            new ShipmentException("HLX-10071233", "EU", "ADDRESS_INVALID", "LOW",
                    "2026-08-25T05:12:00Z", "Request corrected delivery address from shipper"),
            new ShipmentException("HLX-10088991", "NA", "DAMAGE_REPORTED", "HIGH",
                    "2026-08-25T03:40:00Z", "Open claim with HELIOS-GROUND; dispatch replacement"));

    // Rough great-circle transit reference between Helios hubs, in days by air.
    private static final Map<String, Integer> LANE_DAYS = Map.of(
            "FRA-YYZ", 2, "SIN-JFK", 3, "AMS-SIN", 3, "FRA-SIN", 3,
            "YYZ-FRA", 2, "JFK-SIN", 3, "AMS-YYZ", 2);

    public ShipmentStatus getShipmentStatus(String trackingId) {
        ShipmentStatus status = SHIPMENTS.get(trackingId.toUpperCase());
        if (status == null) {
            throw new IllegalArgumentException("Unknown tracking ID: " + trackingId);
        }
        return status;
    }

    public InventoryItem getWarehouseInventory(String sku) {
        InventoryItem item = INVENTORY.get(sku.toUpperCase());
        if (item == null) {
            throw new IllegalArgumentException("Unknown SKU: " + sku);
        }
        return item;
    }

    public DeliveryEstimate estimateDelivery(String originHub, String destinationHub, double weightKg) {
        if (weightKg <= 0) {
            throw new IllegalArgumentException("weightKg must be greater than zero");
        }
        String origin = originHub.toUpperCase();
        String dest = destinationHub.toUpperCase();
        int baseDays = LANE_DAYS.getOrDefault(origin + "-" + dest, 4);
        String service = weightKg > 500 ? "AIR_FREIGHT_HEAVY" : "AIR_EXPRESS";
        double cost = 45.0 + (weightKg * 3.25) + (baseDays * 60.0);
        String formatted = String.format("%,.2f", cost);
        return new DeliveryEstimate(origin, dest, weightKg, service, baseDays, formatted, "EUR");
    }

    public CarrierSla getCarrierSla(String carrierId) {
        CarrierSla sla = CARRIERS.get(carrierId.toUpperCase());
        if (sla == null) {
            throw new IllegalArgumentException("Unknown carrier: " + carrierId);
        }
        return sla;
    }

    public List<ShipmentException> listOpenExceptions(String region) {
        String normalized = region.toUpperCase();
        List<ShipmentException> matches = EXCEPTIONS.stream()
                .filter(e -> e.region().equals(normalized))
                .toList();
        if (matches.isEmpty() && !List.of("EU", "APAC", "NA").contains(normalized)) {
            throw new IllegalArgumentException("Unknown region: " + region + " (expected EU, APAC, or NA)");
        }
        return matches;
    }

    private static InventoryItem inv(String sku, String desc, String wh, int onHand, int reserved, int reorder) {
        int available = onHand - reserved;
        return new InventoryItem(sku, desc, wh, onHand, reserved, available, reorder, available < reorder);
    }
}
