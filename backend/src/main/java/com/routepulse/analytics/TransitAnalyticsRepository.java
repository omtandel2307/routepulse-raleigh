package com.routepulse.analytics;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class TransitAnalyticsRepository {

  private static final int ON_TIME_THRESHOLD_SECONDS = 300;
  private final JdbcClient jdbc;

  public TransitAnalyticsRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public AnalyticsSummary summary(String agencyId, String routeId, Instant cutoff, Instant now) {
    String routeFilter = routeId == null ? "" : " AND route_id = :routeId\n";
    JdbcClient.StatementSpec statement = jdbc.sql("""
            SELECT COUNT(*) AS sample_count,
                   COUNT(DISTINCT route_id) AS route_count,
                   COALESCE(ROUND(100.0 * COUNT(*) FILTER (
                     WHERE delay_seconds BETWEEN -300 AND 300
                   ) / NULLIF(COUNT(*), 0), 1), 0) AS reliability_percent,
                   CAST(ROUND(AVG(delay_seconds)) AS INTEGER) AS average_delay_seconds,
                   COUNT(*) FILTER (WHERE delay_seconds < -300) AS early_samples,
                   COUNT(*) FILTER (WHERE delay_seconds BETWEEN -300 AND 300) AS on_time_samples,
                   COUNT(*) FILTER (WHERE delay_seconds > 300) AS late_samples,
                   MIN(observed_at) AS first_observation,
                   MAX(observed_at) AS last_observation
            FROM trip_delay_observation
            WHERE agency_id = :agencyId AND observed_at >= :cutoff
            """ + routeFilter)
        .param("agencyId", agencyId)
        .param("cutoff", Timestamp.from(cutoff));
    if (routeId != null) {
      statement = statement.param("routeId", routeId);
    }
    return statement.query((row, number) -> new AnalyticsSummary(
        agencyId,
        routeId,
        cutoff,
        now,
        row.getLong("sample_count"),
        row.getInt("route_count"),
        row.getDouble("reliability_percent"),
        row.getObject("average_delay_seconds", Integer.class),
        row.getLong("early_samples"),
        row.getLong("on_time_samples"),
        row.getLong("late_samples"),
        instant(row.getObject("first_observation", OffsetDateTime.class)),
        instant(row.getObject("last_observation", OffsetDateTime.class))
    )).single();
  }

  public List<TimelinePoint> timeline(
      String agencyId, String routeId, Instant cutoff, int bucketMinutes) {
    String routeFilter = routeId == null ? "" : " AND route_id = :routeId\n";
    JdbcClient.StatementSpec statement = jdbc.sql("""
            SELECT to_timestamp(
                     FLOOR(EXTRACT(EPOCH FROM observed_at) / (:bucketSeconds)) * :bucketSeconds
                   ) AS bucket_start,
                   COUNT(*) AS sample_count,
                   COALESCE(ROUND(100.0 * COUNT(*) FILTER (
                     WHERE delay_seconds BETWEEN -300 AND 300
                   ) / NULLIF(COUNT(*), 0), 1), 0) AS reliability_percent,
                   CAST(ROUND(AVG(delay_seconds)) AS INTEGER) AS average_delay_seconds,
                   COUNT(*) FILTER (WHERE delay_seconds < -300) AS early_samples,
                   COUNT(*) FILTER (WHERE delay_seconds BETWEEN -300 AND 300) AS on_time_samples,
                   COUNT(*) FILTER (WHERE delay_seconds > 300) AS late_samples
            FROM trip_delay_observation
            WHERE agency_id = :agencyId AND observed_at >= :cutoff
            """ + routeFilter + """
            GROUP BY bucket_start
            ORDER BY bucket_start
            """)
        .param("agencyId", agencyId)
        .param("cutoff", Timestamp.from(cutoff))
        .param("bucketSeconds", bucketMinutes * 60L);
    if (routeId != null) {
      statement = statement.param("routeId", routeId);
    }
    return statement.query((row, number) -> new TimelinePoint(
        instant(row.getObject("bucket_start", OffsetDateTime.class)),
        row.getLong("sample_count"),
        row.getDouble("reliability_percent"),
        row.getObject("average_delay_seconds", Integer.class),
        row.getLong("early_samples"),
        row.getLong("on_time_samples"),
        row.getLong("late_samples")
    )).list();
  }

  public List<RoutePerformance> routePerformance(String agencyId, Instant cutoff) {
    return jdbc.sql("""
            SELECT route.route_id, route.short_name, route.long_name, route.color,
                   COUNT(observation.trip_id) AS sample_count,
                   COALESCE(ROUND(100.0 * COUNT(observation.trip_id) FILTER (
                     WHERE observation.delay_seconds BETWEEN -300 AND 300
                   ) / NULLIF(COUNT(observation.trip_id), 0), 1), 0) AS reliability_percent,
                   CAST(ROUND(AVG(observation.delay_seconds)) AS INTEGER) AS average_delay_seconds,
                   COUNT(observation.trip_id) FILTER (
                     WHERE observation.delay_seconds < -300) AS early_samples,
                   COUNT(observation.trip_id) FILTER (
                     WHERE observation.delay_seconds BETWEEN -300 AND 300) AS on_time_samples,
                   COUNT(observation.trip_id) FILTER (
                     WHERE observation.delay_seconds > 300) AS late_samples
            FROM transit_route route
            LEFT JOIN trip_delay_observation observation
              ON observation.agency_id = route.agency_id
             AND observation.route_id = route.route_id
             AND observation.observed_at >= :cutoff
            WHERE route.agency_id = :agencyId
            GROUP BY route.route_id, route.short_name, route.long_name, route.color
            ORDER BY sample_count DESC, reliability_percent DESC, route.short_name
            """)
        .param("agencyId", agencyId)
        .param("cutoff", Timestamp.from(cutoff))
        .query((row, number) -> new RoutePerformance(
            row.getString("route_id"),
            row.getString("short_name"),
            row.getString("long_name"),
            row.getString("color"),
            row.getLong("sample_count"),
            row.getDouble("reliability_percent"),
            row.getObject("average_delay_seconds", Integer.class),
            row.getLong("early_samples"),
            row.getLong("on_time_samples"),
            row.getLong("late_samples")
        )).list();
  }

  static boolean isOnTime(int delaySeconds) {
    return delaySeconds >= -ON_TIME_THRESHOLD_SECONDS && delaySeconds <= ON_TIME_THRESHOLD_SECONDS;
  }

  private static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  public record AnalyticsSummary(
      String agencyId,
      String routeId,
      Instant from,
      Instant to,
      long sampleCount,
      int routeCount,
      double reliabilityPercent,
      Integer averageDelaySeconds,
      long earlySamples,
      long onTimeSamples,
      long lateSamples,
      Instant firstObservation,
      Instant lastObservation
  ) {
  }

  public record TimelinePoint(
      Instant bucketStart,
      long sampleCount,
      double reliabilityPercent,
      Integer averageDelaySeconds,
      long earlySamples,
      long onTimeSamples,
      long lateSamples
  ) {
  }

  public record RoutePerformance(
      String routeId,
      String shortName,
      String longName,
      String color,
      long sampleCount,
      double reliabilityPercent,
      Integer averageDelaySeconds,
      long earlySamples,
      long onTimeSamples,
      long lateSamples
  ) {
  }
}
