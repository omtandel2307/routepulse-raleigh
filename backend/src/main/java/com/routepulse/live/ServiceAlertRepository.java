package com.routepulse.live;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Repository
public class ServiceAlertRepository {

  private final JdbcClient jdbc;

  public ServiceAlertRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<ServiceAlertView> activeAlerts(String agencyId, String routeId, Instant now) {
    String routeFilter = routeId == null ? "" : """
        AND (
          NOT EXISTS (
            SELECT 1 FROM service_alert_route target
            WHERE target.agency_id = alert.agency_id AND target.alert_id = alert.alert_id
          )
          OR EXISTS (
            SELECT 1 FROM service_alert_route target
            WHERE target.agency_id = alert.agency_id AND target.alert_id = alert.alert_id
              AND target.route_id = :routeId
          )
        )
        """;
    var query = jdbc.sql("""
            SELECT alert.agency_id, alert.alert_id, alert.cause, alert.effect, alert.header,
                   alert.description, alert.url, alert.recorded_at,
                   ARRAY(
                     SELECT target.route_id FROM service_alert_route target
                     WHERE target.agency_id = alert.agency_id AND target.alert_id = alert.alert_id
                     ORDER BY target.route_id
                   ) AS route_ids,
                   ARRAY(
                     SELECT target.stop_id FROM service_alert_stop target
                     WHERE target.agency_id = alert.agency_id AND target.alert_id = alert.alert_id
                     ORDER BY target.stop_id
                   ) AS stop_ids,
                   (SELECT MIN(period.starts_at) FROM service_alert_period period
                    WHERE period.agency_id = alert.agency_id AND period.alert_id = alert.alert_id) AS starts_at,
                   (SELECT MAX(period.ends_at) FROM service_alert_period period
                    WHERE period.agency_id = alert.agency_id AND period.alert_id = alert.alert_id) AS ends_at
            FROM current_service_alert alert
            WHERE alert.agency_id = :agencyId
              AND (
                NOT EXISTS (
                  SELECT 1 FROM service_alert_period period
                  WHERE period.agency_id = alert.agency_id AND period.alert_id = alert.alert_id
                )
                OR EXISTS (
                  SELECT 1 FROM service_alert_period period
                  WHERE period.agency_id = alert.agency_id AND period.alert_id = alert.alert_id
                    AND (period.starts_at IS NULL OR period.starts_at <= :now)
                    AND (period.ends_at IS NULL OR period.ends_at > :now)
                )
              )
            """ + routeFilter + " ORDER BY alert.recorded_at DESC, alert.alert_id")
        .param("agencyId", agencyId)
        .param("now", Timestamp.from(now));
    if (routeId != null) {
      query = query.param("routeId", routeId);
    }
    return query.query(this::alert).list();
  }

  private ServiceAlertView alert(ResultSet row, int number) throws SQLException {
    return new ServiceAlertView(row.getString("agency_id"), row.getString("alert_id"),
        row.getString("cause"), row.getString("effect"), row.getString("header"),
        row.getString("description"), row.getString("url"), strings(row.getArray("route_ids")),
        strings(row.getArray("stop_ids")), instant(row, "starts_at"), instant(row, "ends_at"),
        row.getTimestamp("recorded_at").toInstant());
  }

  private static List<String> strings(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    return Arrays.asList((String[]) array.getArray());
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    var timestamp = row.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  public record ServiceAlertView(
      String agencyId,
      String alertId,
      String cause,
      String effect,
      String header,
      String description,
      String url,
      List<String> routeIds,
      List<String> stopIds,
      Instant startsAt,
      Instant endsAt,
      Instant recordedAt) {
  }
}
