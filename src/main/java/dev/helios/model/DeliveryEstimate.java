package dev.helios.model;

/** Delivery estimate for a lane, computed from hub distance and weight. */
public record DeliveryEstimate(
        String originHub,
        String destinationHub,
        double weightKg,
        String service,
        int estimatedTransitDays,
        String estimatedCost,
        String currency
) {
}
