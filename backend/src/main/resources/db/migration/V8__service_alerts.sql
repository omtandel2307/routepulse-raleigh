CREATE TABLE current_service_alert (
    agency_id VARCHAR(64) NOT NULL,
    alert_id VARCHAR(256) NOT NULL,
    cause VARCHAR(64) NOT NULL,
    effect VARCHAR(64) NOT NULL,
    header TEXT NOT NULL,
    description TEXT,
    url TEXT,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (agency_id, alert_id),
    CONSTRAINT fk_service_alert_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);

CREATE TABLE service_alert_route (
    agency_id VARCHAR(64) NOT NULL,
    alert_id VARCHAR(256) NOT NULL,
    route_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (agency_id, alert_id, route_id),
    CONSTRAINT fk_alert_route_alert FOREIGN KEY (agency_id, alert_id)
        REFERENCES current_service_alert(agency_id, alert_id) ON DELETE CASCADE
);

CREATE INDEX idx_service_alert_route_route ON service_alert_route(agency_id, route_id);

CREATE TABLE service_alert_stop (
    agency_id VARCHAR(64) NOT NULL,
    alert_id VARCHAR(256) NOT NULL,
    stop_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (agency_id, alert_id, stop_id),
    CONSTRAINT fk_alert_stop_alert FOREIGN KEY (agency_id, alert_id)
        REFERENCES current_service_alert(agency_id, alert_id) ON DELETE CASCADE
);

CREATE INDEX idx_service_alert_stop_stop ON service_alert_stop(agency_id, stop_id);

CREATE TABLE service_alert_period (
    agency_id VARCHAR(64) NOT NULL,
    alert_id VARCHAR(256) NOT NULL,
    period_index INTEGER NOT NULL,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    PRIMARY KEY (agency_id, alert_id, period_index),
    CONSTRAINT fk_alert_period_alert FOREIGN KEY (agency_id, alert_id)
        REFERENCES current_service_alert(agency_id, alert_id) ON DELETE CASCADE
);

CREATE INDEX idx_service_alert_period_active ON service_alert_period(agency_id, starts_at, ends_at);
