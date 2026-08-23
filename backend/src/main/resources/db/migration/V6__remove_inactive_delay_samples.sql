DELETE FROM trip_delay_observation observation
WHERE NOT EXISTS (
    SELECT 1
    FROM vehicle_position vehicle
    WHERE vehicle.agency_id = observation.agency_id
      AND vehicle.trip_id = observation.trip_id
      AND vehicle.recorded_at BETWEEN observation.observed_at - INTERVAL '5 minutes'
                                  AND observation.observed_at + INTERVAL '5 minutes'
);
