CREATE TABLE transit_route (
    agency_id VARCHAR(64) NOT NULL,
    route_id VARCHAR(128) NOT NULL,
    short_name VARCHAR(128),
    long_name VARCHAR(255),
    route_type SMALLINT NOT NULL,
    color VARCHAR(6),
    text_color VARCHAR(6),
    PRIMARY KEY (agency_id, route_id),
    CONSTRAINT fk_route_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);

CREATE TABLE transit_stop (
    agency_id VARCHAR(64) NOT NULL,
    stop_id VARCHAR(128) NOT NULL,
    stop_code VARCHAR(64),
    name VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_type SMALLINT,
    wheelchair_boarding SMALLINT,
    PRIMARY KEY (agency_id, stop_id),
    CONSTRAINT fk_stop_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);

CREATE TABLE service_calendar (
    agency_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(128) NOT NULL,
    monday BOOLEAN NOT NULL,
    tuesday BOOLEAN NOT NULL,
    wednesday BOOLEAN NOT NULL,
    thursday BOOLEAN NOT NULL,
    friday BOOLEAN NOT NULL,
    saturday BOOLEAN NOT NULL,
    sunday BOOLEAN NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    PRIMARY KEY (agency_id, service_id),
    CONSTRAINT fk_calendar_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);

CREATE TABLE service_exception (
    agency_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(128) NOT NULL,
    service_date DATE NOT NULL,
    exception_type SMALLINT NOT NULL,
    PRIMARY KEY (agency_id, service_id, service_date),
    CONSTRAINT fk_exception_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);

CREATE TABLE transit_trip (
    agency_id VARCHAR(64) NOT NULL,
    trip_id VARCHAR(128) NOT NULL,
    route_id VARCHAR(128) NOT NULL,
    service_id VARCHAR(128) NOT NULL,
    headsign VARCHAR(255),
    direction_id SMALLINT,
    shape_id VARCHAR(128),
    PRIMARY KEY (agency_id, trip_id),
    CONSTRAINT fk_trip_agency FOREIGN KEY (agency_id) REFERENCES agency(id),
    CONSTRAINT fk_trip_route FOREIGN KEY (agency_id, route_id) REFERENCES transit_route(agency_id, route_id)
);

CREATE TABLE stop_time (
    agency_id VARCHAR(64) NOT NULL,
    trip_id VARCHAR(128) NOT NULL,
    stop_sequence INTEGER NOT NULL,
    stop_id VARCHAR(128) NOT NULL,
    arrival_seconds INTEGER,
    departure_seconds INTEGER,
    pickup_type SMALLINT,
    drop_off_type SMALLINT,
    timepoint SMALLINT,
    shape_dist_traveled DOUBLE PRECISION,
    PRIMARY KEY (agency_id, trip_id, stop_sequence),
    CONSTRAINT fk_stop_time_trip FOREIGN KEY (agency_id, trip_id) REFERENCES transit_trip(agency_id, trip_id),
    CONSTRAINT fk_stop_time_stop FOREIGN KEY (agency_id, stop_id) REFERENCES transit_stop(agency_id, stop_id)
);

CREATE INDEX idx_trip_route ON transit_trip(agency_id, route_id);
CREATE INDEX idx_stop_time_stop ON stop_time(agency_id, stop_id);

CREATE TABLE gtfs_import (
    agency_id VARCHAR(64) PRIMARY KEY,
    source_url TEXT NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    route_count INTEGER NOT NULL,
    stop_count INTEGER NOT NULL,
    trip_count INTEGER NOT NULL,
    stop_time_count INTEGER NOT NULL,
    CONSTRAINT fk_import_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);
