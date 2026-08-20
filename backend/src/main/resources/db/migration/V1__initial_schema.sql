CREATE TABLE agency (id VARCHAR(64) PRIMARY KEY, name VARCHAR(255) NOT NULL, timezone VARCHAR(64) NOT NULL);
CREATE TABLE vehicle_position (id BIGSERIAL PRIMARY KEY, agency_id VARCHAR(64) NOT NULL, vehicle_id VARCHAR(128) NOT NULL, trip_id VARCHAR(128), route_id VARCHAR(128), latitude DOUBLE PRECISION NOT NULL, longitude DOUBLE PRECISION NOT NULL, recorded_at TIMESTAMPTZ NOT NULL, CONSTRAINT fk_vehicle_agency FOREIGN KEY (agency_id) REFERENCES agency(id));
CREATE INDEX idx_vehicle_position_lookup ON vehicle_position(agency_id, vehicle_id, recorded_at DESC);
INSERT INTO agency(id,name,timezone) VALUES ('wolfline','NC State Wolfline','America/New_York');

