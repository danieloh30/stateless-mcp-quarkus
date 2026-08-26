package dev.helios.model;

/** An in-flight shipment that needs operator attention. */
public record ShipmentException(
        String trackingId,
        String region,
        String type,
        String severity,
        String detectedAt,
        String recommendedAction
) {
}
