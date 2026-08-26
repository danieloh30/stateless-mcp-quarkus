-- Seed data loaded by Hibernate into the Dev Services / test PostgreSQL.
-- One statement per line (Hibernate's default import script parser).

INSERT INTO shipment (tracking_id, status, carrier, origin_hub, destination_hub, last_scan, eta, has_exception) VALUES ('HLX-10032291', 'IN_TRANSIT', 'HELIOS-AIR', 'FRA', 'YYZ', '2026-08-24T21:10:00Z / departed FRA gateway', '2026-08-27', false);
INSERT INTO shipment (tracking_id, status, carrier, origin_hub, destination_hub, last_scan, eta, has_exception) VALUES ('HLX-10044817', 'DELIVERED', 'PARTNER-FEDEX', 'SIN', 'JFK', '2026-08-23T14:02:00Z / signed by K. TAN', '2026-08-23', false);
INSERT INTO shipment (tracking_id, status, carrier, origin_hub, destination_hub, last_scan, eta, has_exception) VALUES ('HLX-10051120', 'EXCEPTION', 'PARTNER-DHL', 'AMS', 'SIN', '2026-08-24T08:45:00Z / held at customs (SIN)', '2026-08-29', true);
INSERT INTO shipment (tracking_id, status, carrier, origin_hub, destination_hub, last_scan, eta, has_exception) VALUES ('HLX-10067734', 'OUT_FOR_DELIVERY', 'HELIOS-GROUND', 'YYZ', 'YYZ', '2026-08-25T06:30:00Z / on vehicle for delivery', '2026-08-25', false);

INSERT INTO inventory (sku, description, warehouse, on_hand, reserved, reorder_point) VALUES ('SKU-COLD-4521', 'Cold-chain vaccine tray (2-8C)', 'FRA-DC1', 480, 360, 200);
INSERT INTO inventory (sku, description, warehouse, on_hand, reserved, reorder_point) VALUES ('SKU-ELEC-8830', 'Edge gateway appliance', 'SIN-DC2', 1240, 300, 250);
INSERT INTO inventory (sku, description, warehouse, on_hand, reserved, reorder_point) VALUES ('SKU-AUTO-2210', 'EV battery module', 'YYZ-DC1', 96, 74, 40);

INSERT INTO carrier (carrier_id, name, on_time_percent, avg_transit_days, damage_rate_percent, tier, period) VALUES ('HELIOS-AIR', 'Helios Air Freight', 98.6, 2.1, 0.12, 'PLATINUM', '2026-Q3');
INSERT INTO carrier (carrier_id, name, on_time_percent, avg_transit_days, damage_rate_percent, tier, period) VALUES ('HELIOS-GROUND', 'Helios Ground Network', 96.2, 3.4, 0.28, 'GOLD', '2026-Q3');
INSERT INTO carrier (carrier_id, name, on_time_percent, avg_transit_days, damage_rate_percent, tier, period) VALUES ('PARTNER-DHL', 'DHL (partner lane)', 94.8, 3.9, 0.41, 'SILVER', '2026-Q3');
INSERT INTO carrier (carrier_id, name, on_time_percent, avg_transit_days, damage_rate_percent, tier, period) VALUES ('PARTNER-FEDEX', 'FedEx (partner lane)', 95.5, 3.6, 0.35, 'GOLD', '2026-Q3');

INSERT INTO shipment_issue (id, tracking_id, region, type, severity, detected_at, recommended_action) VALUES (1, 'HLX-10051120', 'APAC', 'CUSTOMS_HOLD', 'HIGH', '2026-08-24T08:45:00Z', 'Submit commercial invoice to SIN customs broker');
INSERT INTO shipment_issue (id, tracking_id, region, type, severity, detected_at, recommended_action) VALUES (2, 'HLX-10098450', 'APAC', 'WEATHER_DELAY', 'MEDIUM', '2026-08-24T22:10:00Z', 'Re-route via KUL hub; notify consignee of +1 day ETA');
INSERT INTO shipment_issue (id, tracking_id, region, type, severity, detected_at, recommended_action) VALUES (3, 'HLX-10071233', 'EU', 'ADDRESS_INVALID', 'LOW', '2026-08-25T05:12:00Z', 'Request corrected delivery address from shipper');
INSERT INTO shipment_issue (id, tracking_id, region, type, severity, detected_at, recommended_action) VALUES (4, 'HLX-10088991', 'NA', 'DAMAGE_REPORTED', 'HIGH', '2026-08-25T03:40:00Z', 'Open claim with HELIOS-GROUND; dispatch replacement');

INSERT INTO lane (id, origin, destination, transit_days) VALUES (1, 'FRA', 'YYZ', 2);
INSERT INTO lane (id, origin, destination, transit_days) VALUES (2, 'SIN', 'JFK', 3);
INSERT INTO lane (id, origin, destination, transit_days) VALUES (3, 'AMS', 'SIN', 3);
INSERT INTO lane (id, origin, destination, transit_days) VALUES (4, 'FRA', 'SIN', 3);
INSERT INTO lane (id, origin, destination, transit_days) VALUES (5, 'YYZ', 'FRA', 2);
INSERT INTO lane (id, origin, destination, transit_days) VALUES (6, 'JFK', 'SIN', 3);
INSERT INTO lane (id, origin, destination, transit_days) VALUES (7, 'AMS', 'YYZ', 2);
