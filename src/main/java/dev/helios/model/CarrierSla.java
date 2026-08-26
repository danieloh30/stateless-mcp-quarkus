package dev.helios.model;

/** Carrier service-level performance metrics for the current quarter. */
public record CarrierSla(
        String carrierId,
        String name,
        double onTimePercent,
        double avgTransitDays,
        double damageRatePercent,
        String tier,
        String period
) {
}
