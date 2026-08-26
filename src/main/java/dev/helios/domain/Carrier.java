package dev.helios.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Carrier service-level metrics row, looked up by carrier ID. */
@Entity
@Table(name = "carrier")
public class Carrier extends PanacheEntityBase {

    @Id
    @Column(name = "carrier_id")
    public String carrierId;

    @Column(name = "name")
    public String name;

    @Column(name = "on_time_percent")
    public double onTimePercent;

    @Column(name = "avg_transit_days")
    public double avgTransitDays;

    @Column(name = "damage_rate_percent")
    public double damageRatePercent;

    @Column(name = "tier")
    public String tier;

    @Column(name = "period")
    public String period;
}
