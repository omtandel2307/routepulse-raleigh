package com.routepulse.live;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class LiveTransitRepository {

  private static final int ON_TIME_THRESHOLD_SECONDS = 300;
  private static final String VEHICLE_SELECT = """
      SELECT vehicle.agency_id, vehicle.vehicle_id, vehicle.trip_id, vehicle.route_id,
             route.short_name AS route_short_name, route.long_name AS route_long_name,
             vehicle.latitude, vehicle.longitude, vehicle.bearing, vehicle.speed,
             vehicle.current_stop_sequence, vehicle.current_status, vehicle.recorded_at,
             trip.delay_seconds, trip.next_stop_id, trip.next_stop_sequence,
             trip.estimated_arrival, trip.estimated_departure
      FROM current_vehicle vehicle
      LEFT JOIN transit_route route
        ON route.agency_id = vehicle.agency_id AND route.route_id = vehicle.route_id
      LEFT JOIN current_trip_update trip
        ON trip.agency_id = vehicle.agency_id AND trip.trip_id = vehicle.trip_id
      """;

  private final JdbcClient jdbc;

  public LiveTransitRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<VehicleView> activeVehicles(String agencyId, Instant cutoff) {
    return jdbc.sql(VEHICLE_SELECT + """
            WHERE vehicle.agency_id = :agencyId AND vehicle.recorded_at >= :cutoff
            ORDER BY vehicle.recorded_at DESC, vehicle.vehicle_id
            """)
        .param("agencyId", agencyId)
        .param("cutoff", OffsetDateTime.ofInstant(cutoff, java.time.ZoneOffset.UTC))
        .query(this::vehicle)
        .list();
  }

  public List<VehicleView> activeVehiclesForRoute(String agencyId, String routeId, Instant cutoff) {
    return jdbc.sql(VEHICLE_SELECT + """
            WHERE vehicle.agency_id = :agencyId AND vehicle.route_id = :routeId
              AND vehicle.recorded_at >= :cutoff
            ORDER BY vehicle.recorded_at DESC, vehicle.vehicle_id
            """)
        .param("agencyId", agencyId)
        .param("routeId", routeId)
        .param("cutoff", OffsetDateTime.ofInstant(cutoff, java.time.ZoneOffset.UTC))
        .query(this::vehicle)
        .list();
  }

  public Optional<RouteStatus> routeStatus(String agencyId, String routeId, Instant cutoff) {
    return jdbc.sql("""
            SELECT route.route_id, route.short_name, route.long_name,
                   COUNT(vehicle.vehicle_id) AS active_vehicles,
                   COUNT(*) FILTER (WHERE trip.delay_seconds < -300) AS early_vehicles,
                   COUNT(*) FILTER (WHERE trip.delay_seconds BETWEEN -300 AND 300) AS on_time_vehicles,
                   COUNT(*) FILTER (WHERE trip.delay_seconds > 300) AS late_vehicles,
                   COUNT(vehicle.vehicle_id) FILTER (WHERE trip.delay_seconds IS NULL) AS unknown_vehicles,
                   CAST(ROUND(AVG(trip.delay_seconds)) AS integer) AS average_delay_seconds,
                   MAX(vehicle.recorded_at) AS last_updated
            FROM transit_route route
            LEFT JOIN current_vehicle vehicle
              ON vehicle.agency_id = route.agency_id AND vehicle.route_id = route.route_id
             AND vehicle.recorded_at >= :cutoff
            LEFT JOIN current_trip_update trip
              ON trip.agency_id = vehicle.agency_id AND trip.trip_id = vehicle.trip_id
            WHERE route.agency_id = :agencyId AND route.route_id = :routeId
            GROUP BY route.route_id, route.short_name, route.long_name
            """)
        .param("agencyId", agencyId)
        .param("routeId", routeId)
        .param("cutoff", OffsetDateTime.ofInstant(cutoff, java.time.ZoneOffset.UTC))
        .query((row, number) -> new RouteStatus(
            agencyId,
            row.getString("route_id"),
            row.getString("short_name"),
            row.getString("long_name"),
            row.getLong("active_vehicles"),
            row.getLong("early_vehicles"),
            row.getLong("on_time_vehicles"),
            row.getLong("late_vehicles"),
            row.getLong("unknown_vehicles"),
            row.getObject("average_delay_seconds", Integer.class),
            instant(row, "last_updated")
        ))
        .optional();
  }

  public List<StopArrival> stopArrivals(String agencyId, String stopId, Instant cutoff) {
    return jdbc.sql("""
            SELECT update.trip_id, update.route_id, route.short_name, route.long_name,
                   update.vehicle_id, update.estimated_arrival, update.estimated_departure,
                   update.delay_seconds, update.recorded_at
            FROM current_trip_update update
            LEFT JOIN transit_route route
              ON route.agency_id = update.agency_id AND route.route_id = update.route_id
            WHERE update.agency_id = :agencyId
              AND update.next_stop_id = :stopId
              AND update.recorded_at >= :cutoff
              AND COALESCE(update.estimated_arrival, update.estimated_departure) >= now() - interval '1 minute'
            ORDER BY COALESCE(update.estimated_arrival, update.estimated_departure) NULLS LAST,
                     update.trip_id
            """)
        .param("agencyId", agencyId)
        .param("stopId", stopId)
        .param("cutoff", OffsetDateTime.ofInstant(cutoff, java.time.ZoneOffset.UTC))
        .query((row, number) -> new StopArrival(
            agencyId,
            stopId,
            row.getString("trip_id"),
            row.getString("route_id"),
            row.getString("short_name"),
            row.getString("long_name"),
            row.getString("vehicle_id"),
            instant(row, "estimated_arrival"),
            instant(row, "estimated_departure"),
            row.getObject("delay_seconds", Integer.class),
            instant(row, "recorded_at")
        ))
        .list();
  }

  private VehicleView vehicle(ResultSet row, int number) throws SQLException {
    Integer delay = row.getObject("delay_seconds", Integer.class);
    return new VehicleView(
        row.getString("agency_id"),
        row.getString("vehicle_id"),
        row.getString("trip_id"),
        row.getString("route_id"),
        row.getString("route_short_name"),
        row.getString("route_long_name"),
        row.getDouble("latitude"),
        row.getDouble("longitude"),
        row.getObject("bearing", Float.class),
        row.getObject("speed", Float.class),
        row.getObject("current_stop_sequence", Integer.class),
        row.getString("current_status"),
        delay,
        delayStatus(delay),
        row.getString("next_stop_id"),
        row.getObject("next_stop_sequence", Integer.class),
        instant(row, "estimated_arrival"),
        instant(row, "estimated_departure"),
        instant(row, "recorded_at")
    );
  }

  static DelayStatus delayStatus(Integer delaySeconds) {
    if (delaySeconds == null) {
      return DelayStatus.UNKNOWN;
    }
    if (delaySeconds < -ON_TIME_THRESHOLD_SECONDS) {
      return DelayStatus.EARLY;
    }
    if (delaySeconds > ON_TIME_THRESHOLD_SECONDS) {
      return DelayStatus.LATE;
    }
    return DelayStatus.ON_TIME;
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  public enum DelayStatus {
    EARLY,
    ON_TIME,
    LATE,
    UNKNOWN
  }

  public record VehicleView(
      String agencyId,
      String vehicleId,
      String tripId,
      String routeId,
      String routeShortName,
      String routeLongName,
      double latitude,
      double longitude,
      Float bearing,
      Float speed,
      Integer currentStopSequence,
      String currentStatus,
      Integer delaySeconds,
      DelayStatus delayStatus,
      String nextStopId,
      Integer nextStopSequence,
      Instant estimatedArrival,
      Instant estimatedDeparture,
      Instant recordedAt
  ) {
  }

  public record RouteStatus(
      String agencyId,
      String routeId,
      String shortName,
      String longName,
      long activeVehicles,
      long earlyVehicles,
      long onTimeVehicles,
      long lateVehicles,
      long unknownVehicles,
      Integer averageDelaySeconds,
      Instant lastUpdated
  ) {
  }

  public record StopArrival(
      String agencyId,
      String stopId,
      String tripId,
      String routeId,
      String routeShortName,
      String routeLongName,
      String vehicleId,
      Instant estimatedArrival,
      Instant estimatedDeparture,
      Integer delaySeconds,
      Instant recordedAt
  ) {
  }
}
