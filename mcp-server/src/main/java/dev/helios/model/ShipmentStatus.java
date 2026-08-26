package dev.helios.model;

/** Current status of a shipment as returned by the tracking lookup tool. */
public record ShipmentStatus(
        String trackingId,
        String status,
        String carrier,
        String originHub,
        String destinationHub,
        String lastScan,
        String eta,
        boolean hasException
) {
}
