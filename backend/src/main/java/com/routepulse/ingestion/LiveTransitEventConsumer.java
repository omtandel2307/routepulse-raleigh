package com.routepulse.ingestion;

import com.routepulse.config.KafkaConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class LiveTransitEventConsumer {

  private final JdbcTemplate jdbc;

  public LiveTransitEventConsumer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  @KafkaListener(topics = KafkaConfig.VEHICLE_POSITIONS_TOPIC)
  public void consumeVehiclePosition(VehiclePositionEvent event) {
    Timestamp recordedAt = Timestamp.from(event.recordedAt());
    Timestamp receivedAt = Timestamp.from(Instant.now());

    jdbc.update("""
            INSERT INTO vehicle_position(agency_id, vehicle_id, trip_id, route_id, latitude, longitude,
                                         recorded_at, bearing, speed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (agency_id, vehicle_id, recorded_at) DO NOTHING
            """, event.agencyId(), event.vehicleId(), event.tripId(), event.routeId(), event.latitude(),
        event.longitude(), recordedAt, event.bearing(), event.speed());

    jdbc.update("""
            INSERT INTO current_vehicle(agency_id, vehicle_id, trip_id, route_id, latitude, longitude,
                                        bearing, speed, current_stop_sequence, current_status,
                                        recorded_at, received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (agency_id, vehicle_id) DO UPDATE SET
              trip_id = EXCLUDED.trip_id,
              route_id = EXCLUDED.route_id,
              latitude = EXCLUDED.latitude,
              longitude = EXCLUDED.longitude,
              bearing = EXCLUDED.bearing,
              speed = EXCLUDED.speed,
              current_stop_sequence = EXCLUDED.current_stop_sequence,
              current_status = EXCLUDED.current_status,
              recorded_at = EXCLUDED.recorded_at,
              received_at = EXCLUDED.received_at
            WHERE EXCLUDED.recorded_at >= current_vehicle.recorded_at
            """, event.agencyId(), event.vehicleId(), event.tripId(), event.routeId(), event.latitude(),
        event.longitude(), event.bearing(), event.speed(), event.currentStopSequence(),
        event.currentStatus(), recordedAt, receivedAt);
  }

  @Transactional
  @KafkaListener(topics = KafkaConfig.TRIP_UPDATES_TOPIC)
  public void consumeTripUpdate(TripUpdateEvent event) {
    Timestamp recordedAt = Timestamp.from(event.recordedAt());
    Timestamp receivedAt = Timestamp.from(Instant.now());

    jdbc.update("""
            INSERT INTO current_trip_update(agency_id, trip_id, route_id, vehicle_id, delay_seconds,
                                            next_stop_id, next_stop_sequence, estimated_arrival,
                                            estimated_departure, recorded_at, received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (agency_id, trip_id) DO UPDATE SET
              route_id = EXCLUDED.route_id,
              vehicle_id = EXCLUDED.vehicle_id,
              delay_seconds = EXCLUDED.delay_seconds,
              next_stop_id = EXCLUDED.next_stop_id,
              next_stop_sequence = EXCLUDED.next_stop_sequence,
              estimated_arrival = EXCLUDED.estimated_arrival,
              estimated_departure = EXCLUDED.estimated_departure,
              recorded_at = EXCLUDED.recorded_at,
              received_at = EXCLUDED.received_at
            WHERE EXCLUDED.recorded_at >= current_trip_update.recorded_at
            """, event.agencyId(), event.tripId(), event.routeId(), event.vehicleId(),
        event.delaySeconds(), event.nextStopId(), event.nextStopSequence(), timestamp(event.estimatedArrival()),
        timestamp(event.estimatedDeparture()), recordedAt, receivedAt);
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }
}
