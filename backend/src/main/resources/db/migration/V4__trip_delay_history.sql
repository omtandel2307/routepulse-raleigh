CREATE TABLE trip_delay_observation (
    agency_id VARCHAR(64) NOT NULL,
    trip_id VARCHAR(128) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    route_id VARCHAR(128) NOT NULL,
    vehicle_id VARCHAR(128),
    stop_id VARCHAR(128),
    stop_sequence INTEGER,
    delay_seconds INTEGER NOT NULL,
    estimated_event_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (agency_id, trip_id, observed_at),
    CONSTRAINT fk_delay_observation_agency FOREIGN KEY (agency_id) REFERENCES agency(id),
    CONSTRAINT fk_delay_observation_route FOREIGN KEY (agency_id, route_id)
        REFERENCES transit_route(agency_id, route_id)
);

CREATE INDEX idx_delay_observation_route_time
    ON trip_delay_observation(agency_id, route_id, observed_at DESC);
CREATE INDEX idx_delay_observation_time
    ON trip_delay_observation(agency_id, observed_at DESC);

-- Seed the first analytics points from the current active state. New observations are sampled
-- once per trip per minute by the Kafka consumer.
INSERT INTO trip_delay_observation(
    agency_id, trip_id, observed_at, route_id, vehicle_id, stop_id, stop_sequence,
    delay_seconds, estimated_event_at, received_at
)
SELECT vehicle.agency_id,
       vehicle.trip_id,
       date_trunc('minute', vehicle.recorded_at),
       vehicle.route_id,
       vehicle.vehicle_id,
       update.next_stop_id,
       update.next_stop_sequence,
       ROUND(
         EXTRACT(EPOCH FROM (
           (COALESCE(update.estimated_arrival, update.estimated_departure) AT TIME ZONE agency.timezone)
           - date_trunc('day', COALESCE(update.estimated_arrival, update.estimated_departure)
               AT TIME ZONE agency.timezone)
         )) - COALESCE(stop.arrival_seconds, stop.departure_seconds)
       )::INTEGER,
       COALESCE(update.estimated_arrival, update.estimated_departure),
       now()
FROM current_vehicle vehicle
JOIN current_trip_update update
  ON update.agency_id = vehicle.agency_id AND update.trip_id = vehicle.trip_id
JOIN agency ON agency.id = vehicle.agency_id
JOIN stop_time stop
  ON stop.agency_id = update.agency_id
 AND stop.trip_id = update.trip_id
 AND stop.stop_sequence = update.next_stop_sequence
WHERE vehicle.trip_id IS NOT NULL
  AND vehicle.route_id IS NOT NULL
  AND COALESCE(update.estimated_arrival, update.estimated_departure) IS NOT NULL
  AND COALESCE(stop.arrival_seconds, stop.departure_seconds) IS NOT NULL
ON CONFLICT DO NOTHING;
