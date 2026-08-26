package dev.helios.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Warehouse stock row, looked up by SKU. */
@Entity
@Table(name = "inventory")
public class Inventory extends PanacheEntityBase {

    @Id
    @Column(name = "sku")
    public String sku;

    @Column(name = "description")
    public String description;

    @Column(name = "warehouse")
    public String warehouse;

    @Column(name = "on_hand")
    public int onHand;

    @Column(name = "reserved")
    public int reserved;

    @Column(name = "reorder_point")
    public int reorderPoint;
}
