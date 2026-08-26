package dev.helios.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An open shipment exception requiring operator attention, filtered by region. */
@Entity
@Table(name = "shipment_issue")
public class ShipmentIssue extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public Long id;

    @Column(name = "tracking_id")
    public String trackingId;

    @Column(name = "region")
    public String region;

    @Column(name = "type")
    public String type;

    @Column(name = "severity")
    public String severity;

    @Column(name = "detected_at")
    public String detectedAt;

    @Column(name = "recommended_action")
    public String recommendedAction;
}
