CREATE TABLE transit_shape_point (
    agency_id VARCHAR(64) NOT NULL,
    shape_id VARCHAR(128) NOT NULL,
    sequence INTEGER NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    distance_traveled DOUBLE PRECISION,
    PRIMARY KEY (agency_id, shape_id, sequence),
    CONSTRAINT fk_shape_point_agency FOREIGN KEY (agency_id) REFERENCES agency(id)
);

CREATE INDEX idx_shape_point_shape
    ON transit_shape_point(agency_id, shape_id, sequence);

ALTER TABLE gtfs_import
    ADD COLUMN shape_point_count INTEGER NOT NULL DEFAULT 0;

-- Historical observations remain useful across replacement GTFS imports. Route IDs are retained
-- as source identifiers but deliberately do not cascade away when the schedule is refreshed.
ALTER TABLE trip_delay_observation
    DROP CONSTRAINT fk_delay_observation_route;
