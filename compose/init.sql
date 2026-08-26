-- Schema + seed for the SHARED PostgreSQL used by the cluster demo.
-- Loaded once by the Postgres container on first start (docker-entrypoint-initdb.d).
-- The stateless app replicas run with hibernate generation=none and only read.

CREATE TABLE shipment (
    tracking_id      VARCHAR(32) PRIMARY KEY,
    status           VARCHAR(32),
    carrier          VARCHAR(40),
    origin_hub       VARCHAR(8),
    destination_hub  VARCHAR(8),
    last_scan        VARCHAR(128),
    eta              VARCHAR(32),
    has_exception    BOOLEAN
);

CREATE TABLE inventory (
    sku            VARCHAR(32) PRIMARY KEY,
    description    VARCHAR(128),
    warehouse      VARCHAR(32),
    on_hand        INTEGER,
    reserved       INTEGER,
    reorder_point  INTEGER
);

CREATE TABLE carrier (
    carrier_id           VARCHAR(40) PRIMARY KEY,
    name                 VARCHAR(64),
    on_time_percent      DOUBLE PRECISION,
    avg_transit_days     DOUBLE PRECISION,
    damage_rate_percent  DOUBLE PRECISION,
    tier                 VARCHAR(16),
    period               VARCHAR(16)
);

CREATE TABLE shipment_issue (
    id                  BIGINT PRIMARY KEY,
    tracking_id         VARCHAR(32),
    region              VARCHAR(8),
    type                VARCHAR(32),
    severity            VARCHAR(16),
    detected_at         VARCHAR(32),
    recommended_action  VARCHAR(200)
);

CREATE TABLE lane (
    id            BIGINT PRIMARY KEY,
    origin        VARCHAR(8),
    destination   VARCHAR(8),
    transit_days  INTEGER
);

INSERT INTO shipment (tracking_id, status, carrier, origin_hub, destination_hub, last_scan, eta, has_exception) VALUES
    ('HLX-10032291', 'IN_TRANSIT', 'HELIOS-AIR', 'FRA', 'YYZ', '2026-08-24T21:10:00Z / departed FRA gateway', '2026-08-27', false),
    ('HLX-10044817', 'DELIVERED', 'PARTNER-FEDEX', 'SIN', 'JFK', '2026-08-23T14:02:00Z / signed by K. TAN', '2026-08-23', false),
    ('HLX-10051120', 'EXCEPTION', 'PARTNER-DHL', 'AMS', 'SIN', '2026-08-24T08:45:00Z / held at customs (SIN)', '2026-08-29', true),
    ('HLX-10067734', 'OUT_FOR_DELIVERY', 'HELIOS-GROUND', 'YYZ', 'YYZ', '2026-08-25T06:30:00Z / on vehicle for delivery', '2026-08-25', false);

INSERT INTO inventory (sku, description, warehouse, on_hand, reserved, reorder_point) VALUES
    ('SKU-COLD-4521', 'Cold-chain vaccine tray (2-8C)', 'FRA-DC1', 480, 360, 200),
    ('SKU-ELEC-8830', 'Edge gateway appliance', 'SIN-DC2', 1240, 300, 250),
    ('SKU-AUTO-2210', 'EV battery module', 'YYZ-DC1', 96, 74, 40);

INSERT INTO carrier (carrier_id, name, on_time_percent, avg_transit_days, damage_rate_percent, tier, period) VALUES
    ('HELIOS-AIR', 'Helios Air Freight', 98.6, 2.1, 0.12, 'PLATINUM', '2026-Q3'),
    ('HELIOS-GROUND', 'Helios Ground Network', 96.2, 3.4, 0.28, 'GOLD', '2026-Q3'),
    ('PARTNER-DHL', 'DHL (partner lane)', 94.8, 3.9, 0.41, 'SILVER', '2026-Q3'),
    ('PARTNER-FEDEX', 'FedEx (partner lane)', 95.5, 3.6, 0.35, 'GOLD', '2026-Q3');

INSERT INTO shipment_issue (id, tracking_id, region, type, severity, detected_at, recommended_action) VALUES
    (1, 'HLX-10051120', 'APAC', 'CUSTOMS_HOLD', 'HIGH', '2026-08-24T08:45:00Z', 'Submit commercial invoice to SIN customs broker'),
    (2, 'HLX-10098450', 'APAC', 'WEATHER_DELAY', 'MEDIUM', '2026-08-24T22:10:00Z', 'Re-route via KUL hub; notify consignee of +1 day ETA'),
    (3, 'HLX-10071233', 'EU', 'ADDRESS_INVALID', 'LOW', '2026-08-25T05:12:00Z', 'Request corrected delivery address from shipper'),
    (4, 'HLX-10088991', 'NA', 'DAMAGE_REPORTED', 'HIGH', '2026-08-25T03:40:00Z', 'Open claim with HELIOS-GROUND; dispatch replacement');

INSERT INTO lane (id, origin, destination, transit_days) VALUES
    (1, 'FRA', 'YYZ', 2), (2, 'SIN', 'JFK', 3), (3, 'AMS', 'SIN', 3),
    (4, 'FRA', 'SIN', 3), (5, 'YYZ', 'FRA', 2), (6, 'JFK', 'SIN', 3), (7, 'AMS', 'YYZ', 2);
