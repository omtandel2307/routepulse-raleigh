package com.routepulse.ingestion;

import com.routepulse.config.KafkaConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
    Integer delaySeconds = deriveDelay(event);

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
        delaySeconds, event.nextStopId(), event.nextStopSequence(), timestamp(event.estimatedArrival()),
        timestamp(event.estimatedDeparture()), recordedAt, receivedAt);

    if (delaySeconds != null && event.routeId() != null && isOperationalPrediction(event, delaySeconds)) {
      jdbc.update("""
              INSERT INTO trip_delay_observation(
                  agency_id, trip_id, observed_at, route_id, vehicle_id, stop_id, stop_sequence,
                  delay_seconds, estimated_event_at, received_at)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              ON CONFLICT (agency_id, trip_id, observed_at) DO UPDATE SET
                route_id = EXCLUDED.route_id,
                vehicle_id = EXCLUDED.vehicle_id,
                stop_id = EXCLUDED.stop_id,
                stop_sequence = EXCLUDED.stop_sequence,
                delay_seconds = EXCLUDED.delay_seconds,
                estimated_event_at = EXCLUDED.estimated_event_at,
                received_at = EXCLUDED.received_at
              """, event.agencyId(), event.tripId(),
          Timestamp.from(event.recordedAt().truncatedTo(ChronoUnit.MINUTES)), event.routeId(),
          event.vehicleId(), event.nextStopId(), event.nextStopSequence(), delaySeconds,
          timestamp(estimatedEvent(event)), receivedAt);
    }
  }

  private Integer deriveDelay(TripUpdateEvent event) {
    if (event.delaySeconds() != null) {
      return event.delaySeconds();
    }
    Instant estimate = estimatedEvent(event);
    if (estimate == null || event.nextStopSequence() == null) {
      return null;
    }
    List<Integer> delays = jdbc.query("""
            SELECT ROUND(
              EXTRACT(EPOCH FROM (
                (?::timestamptz AT TIME ZONE agency.timezone)
                - date_trunc('day', ?::timestamptz AT TIME ZONE agency.timezone)
              )) - COALESCE(stop.arrival_seconds, stop.departure_seconds)
            )::INTEGER
            FROM stop_time stop
            JOIN agency ON agency.id = stop.agency_id
            WHERE stop.agency_id = ? AND stop.trip_id = ? AND stop.stop_sequence = ?
              AND COALESCE(stop.arrival_seconds, stop.departure_seconds) IS NOT NULL
            """, (row, number) -> row.getInt(1), Timestamp.from(estimate), Timestamp.from(estimate),
        event.agencyId(), event.tripId(), event.nextStopSequence());
    if (delays.isEmpty() || Math.abs(delays.getFirst()) > 7200) {
      return null;
    }
    return delays.getFirst();
  }

  private boolean isOperationalPrediction(TripUpdateEvent event, int delaySeconds) {
    Instant estimate = estimatedEvent(event);
    if (estimate == null
        || estimate.isBefore(event.recordedAt().minus(5, ChronoUnit.MINUTES))
        || estimate.isAfter(event.recordedAt().plus(30, ChronoUnit.MINUTES))) {
      return false;
    }
    Integer activeVehicles = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM current_vehicle
            WHERE agency_id = ? AND trip_id = ?
              AND recorded_at >= ?
            """, Integer.class, event.agencyId(), event.tripId(),
        Timestamp.from(event.recordedAt().minus(5, ChronoUnit.MINUTES)));
    return activeVehicles != null && activeVehicles > 0 && Math.abs(delaySeconds) <= 7200;
  }

  private static Instant estimatedEvent(TripUpdateEvent event) {
    return event.estimatedArrival() != null ? event.estimatedArrival() : event.estimatedDeparture();
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }
}
