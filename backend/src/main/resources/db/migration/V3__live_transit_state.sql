ALTER TABLE vehicle_position ADD COLUMN bearing REAL;
ALTER TABLE vehicle_position ADD COLUMN speed REAL;
CREATE UNIQUE INDEX uq_vehicle_position_event
    ON vehicle_position(agency_id, vehicle_id, recorded_at);

CREATE TABLE current_vehicle (
    agency_id VARCHAR(64) NOT NULL,
    vehicle_id VARCHAR(128) NOT NULL,
    trip_id VARCHAR(128),
    route_id VARCHAR(128),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    bearing REAL,
    speed REAL,
    current_stop_sequence INTEGER,
    current_status VARCHAR(32),
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (agency_id, vehicle_id),
    CONSTRAINT fk_current_vehicle_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);

CREATE INDEX idx_current_vehicle_route ON current_vehicle(agency_id, route_id, recorded_at DESC);
CREATE INDEX idx_current_vehicle_trip ON current_vehicle(agency_id, trip_id);

CREATE TABLE current_trip_update (
    agency_id VARCHAR(64) NOT NULL,
    trip_id VARCHAR(128) NOT NULL,
    route_id VARCHAR(128),
    vehicle_id VARCHAR(128),
    delay_seconds INTEGER,
    next_stop_id VARCHAR(128),
    next_stop_sequence INTEGER,
    estimated_arrival TIMESTAMPTZ,
    estimated_departure TIMESTAMPTZ,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (agency_id, trip_id),
    CONSTRAINT fk_current_trip_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);

CREATE INDEX idx_current_trip_route ON current_trip_update(agency_id, route_id, recorded_at DESC);
