package dev.helios.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reference transit time between two hubs, used to estimate delivery. */
@Entity
@Table(name = "lane")
public class Lane extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public Long id;

    @Column(name = "origin")
    public String origin;

    @Column(name = "destination")
    public String destination;

    @Column(name = "transit_days")
    public int transitDays;
}
