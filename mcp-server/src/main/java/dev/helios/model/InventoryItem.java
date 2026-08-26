package dev.helios.model;

/** Warehouse stock position for a SKU. */
public record InventoryItem(
        String sku,
        String description,
        String warehouse,
        int onHand,
        int reserved,
        int available,
        int reorderPoint,
        boolean belowReorderPoint
) {
}
