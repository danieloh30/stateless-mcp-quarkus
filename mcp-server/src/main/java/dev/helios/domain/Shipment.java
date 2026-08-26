package dev.helios.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A shipment row, looked up by tracking ID. */
@Entity
@Table(name = "shipment")
public class Shipment extends PanacheEntityBase {

    @Id
    @Column(name = "tracking_id")
    public String trackingId;

    @Column(name = "status")
    public String status;

    @Column(name = "carrier")
    public String carrier;

    @Column(name = "origin_hub")
    public String originHub;

    @Column(name = "destination_hub")
    public String destinationHub;

    @Column(name = "last_scan")
    public String lastScan;

    @Column(name = "eta")
    public String eta;

    @Column(name = "has_exception")
    public boolean hasException;
}
