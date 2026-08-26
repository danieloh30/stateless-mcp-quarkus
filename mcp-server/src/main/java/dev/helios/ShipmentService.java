package dev.helios;

import java.util.List;

import dev.helios.domain.Carrier;
import dev.helios.domain.Inventory;
import dev.helios.domain.Lane;
import dev.helios.domain.Shipment;
import dev.helios.domain.ShipmentIssue;
import dev.helios.model.CarrierSla;
import dev.helios.model.DeliveryEstimate;
import dev.helios.model.InventoryItem;
import dev.helios.model.ShipmentException;
import dev.helios.model.ShipmentStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Business logic for Helios Logistics tooling — backed by PostgreSQL.
 *
 * <p>Every method is a self-contained, read-only database lookup: no per-caller
 * session, no conversational memory, no shared mutable state in the JVM. The only
 * state lives in Postgres, which every replica shares. That is what lets the MCP
 * server in front of it run as many identical, stateless replicas behind a load
 * balancer — any instance can serve any request — and scale to zero when idle.
 *
 * <p>Methods are {@code @Transactional} so each call runs in its own short-lived
 * unit of work; nothing is carried over between calls.
 */
@ApplicationScoped
public class ShipmentService {

    @Transactional
    public ShipmentStatus getShipmentStatus(String trackingId) {
        Shipment s = Shipment.findById(trackingId.toUpperCase());
        if (s == null) {
            throw new IllegalArgumentException("Unknown tracking ID: " + trackingId);
        }
        return new ShipmentStatus(s.trackingId, s.status, s.carrier, s.originHub,
                s.destinationHub, s.lastScan, s.eta, s.hasException);
    }

    @Transactional
    public InventoryItem getWarehouseInventory(String sku) {
        Inventory i = Inventory.findById(sku.toUpperCase());
        if (i == null) {
            throw new IllegalArgumentException("Unknown SKU: " + sku);
        }
        int available = i.onHand - i.reserved;
        return new InventoryItem(i.sku, i.description, i.warehouse, i.onHand, i.reserved,
                available, i.reorderPoint, available < i.reorderPoint);
    }

    @Transactional
    public DeliveryEstimate estimateDelivery(String originHub, String destinationHub, double weightKg) {
        if (weightKg <= 0) {
            throw new IllegalArgumentException("weightKg must be greater than zero");
        }
        String origin = originHub.toUpperCase();
        String dest = destinationHub.toUpperCase();
        Lane lane = Lane.find("origin = ?1 and destination = ?2", origin, dest).firstResult();
        int baseDays = lane != null ? lane.transitDays : 4;
        String service = weightKg > 500 ? "AIR_FREIGHT_HEAVY" : "AIR_EXPRESS";
        double cost = 45.0 + (weightKg * 3.25) + (baseDays * 60.0);
        return new DeliveryEstimate(origin, dest, weightKg, service, baseDays,
                String.format("%,.2f", cost), "EUR");
    }

    @Transactional
    public CarrierSla getCarrierSla(String carrierId) {
        Carrier c = Carrier.findById(carrierId.toUpperCase());
        if (c == null) {
            throw new IllegalArgumentException("Unknown carrier: " + carrierId);
        }
        return new CarrierSla(c.carrierId, c.name, c.onTimePercent, c.avgTransitDays,
                c.damageRatePercent, c.tier, c.period);
    }

    @Transactional
    public List<ShipmentException> listOpenExceptions(String region) {
        String normalized = region.toUpperCase();
        if (!List.of("EU", "APAC", "NA").contains(normalized)) {
            throw new IllegalArgumentException("Unknown region: " + region + " (expected EU, APAC, or NA)");
        }
        List<ShipmentIssue> issues = ShipmentIssue.list("region", normalized);
        return issues.stream()
                .map(e -> new ShipmentException(e.trackingId, e.region, e.type, e.severity,
                        e.detectedAt, e.recommendedAction))
                .toList();
    }
}
