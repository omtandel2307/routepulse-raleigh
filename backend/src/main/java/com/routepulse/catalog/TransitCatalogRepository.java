package com.routepulse.catalog;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class TransitCatalogRepository {

  private final JdbcClient jdbc;

  public TransitCatalogRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<RouteView> routes(String agencyId) {
    return jdbc.sql("""
            SELECT route.route_id, route.short_name, route.long_name, route.route_type,
                   route.color, route.text_color, COUNT(trip.trip_id) AS trip_count
            FROM transit_route route
            LEFT JOIN transit_trip trip
              ON trip.agency_id = route.agency_id AND trip.route_id = route.route_id
            WHERE route.agency_id = :agencyId
            GROUP BY route.route_id, route.short_name, route.long_name, route.route_type,
                     route.color, route.text_color
            ORDER BY route.short_name NULLS LAST, route.long_name NULLS LAST
            """)
        .param("agencyId", agencyId)
        .query((row, number) -> new RouteView(
            agencyId,
            row.getString("route_id"),
            row.getString("short_name"),
            row.getString("long_name"),
            row.getInt("route_type"),
            row.getString("color"),
            row.getString("text_color"),
            row.getLong("trip_count")
        ))
        .list();
  }

  public List<StopView> stops(String agencyId) {
    return jdbc.sql("""
            SELECT stop.stop_id, stop.stop_code, stop.name, stop.latitude, stop.longitude,
                   COUNT(DISTINCT stop_time.trip_id) AS trip_count
            FROM transit_stop stop
            LEFT JOIN stop_time
              ON stop_time.agency_id = stop.agency_id AND stop_time.stop_id = stop.stop_id
            WHERE stop.agency_id = :agencyId
            GROUP BY stop.stop_id, stop.stop_code, stop.name, stop.latitude, stop.longitude
            ORDER BY stop.name
            """)
        .param("agencyId", agencyId)
        .query((row, number) -> new StopView(
            agencyId,
            row.getString("stop_id"),
            row.getString("stop_code"),
            row.getString("name"),
            row.getDouble("latitude"),
            row.getDouble("longitude"),
            row.getLong("trip_count")
        ))
        .list();
  }

  public Optional<ImportStatus> importStatus(String agencyId) {
    return jdbc.sql("""
            SELECT agency_id, source_url, imported_at, route_count, stop_count, trip_count,
                   stop_time_count, shape_point_count
            FROM gtfs_import
            WHERE agency_id = :agencyId
            """)
        .param("agencyId", agencyId)
        .query((row, number) -> new ImportStatus(
            row.getString("agency_id"),
            row.getString("source_url"),
            row.getObject("imported_at", java.time.OffsetDateTime.class).toInstant(),
            row.getInt("route_count"),
            row.getInt("stop_count"),
            row.getInt("trip_count"),
            row.getInt("stop_time_count"),
            row.getInt("shape_point_count")
        ))
        .optional();
  }

  public RouteGeometryCollection routeGeometry(String agencyId, String routeId) {
    String routeFilter = routeId == null || routeId.isBlank() ? "" : " AND route.route_id = :routeId";
    var query = jdbc.sql("""
            SELECT route.route_id, route.short_name, route.long_name, route.color,
                   shapes.shape_id, shapes.direction_id, point.sequence, point.latitude, point.longitude
            FROM transit_route route
            JOIN (
              SELECT DISTINCT agency_id, route_id, shape_id, direction_id
              FROM transit_trip
              WHERE shape_id IS NOT NULL
            ) shapes ON shapes.agency_id = route.agency_id AND shapes.route_id = route.route_id
            JOIN transit_shape_point point
              ON point.agency_id = shapes.agency_id AND point.shape_id = shapes.shape_id
            WHERE route.agency_id = :agencyId
            """ + routeFilter + """
            ORDER BY route.short_name NULLS LAST, route.route_id, shapes.shape_id,
                     shapes.direction_id NULLS FIRST, point.sequence
            """).param("agencyId", agencyId);
    if (!routeFilter.isEmpty()) {
      query = query.param("routeId", routeId);
    }

    List<ShapeCoordinate> rows = query.query((row, number) -> new ShapeCoordinate(
        row.getString("route_id"), row.getString("short_name"), row.getString("long_name"),
        row.getString("color"), row.getString("shape_id"),
        row.getObject("direction_id", Integer.class), row.getDouble("longitude"),
        row.getDouble("latitude"))).list();

    Map<String, RouteGeometryBuilder> grouped = new LinkedHashMap<>();
    for (ShapeCoordinate row : rows) {
      String key = row.routeId() + "\u0000" + row.shapeId() + "\u0000" + row.directionId();
      grouped.computeIfAbsent(key, ignored -> new RouteGeometryBuilder(row)).coordinates()
          .add(List.of(row.longitude(), row.latitude()));
    }
    List<RouteGeometryFeature> features = grouped.values().stream()
        .map(RouteGeometryBuilder::feature)
        .toList();
    return new RouteGeometryCollection("FeatureCollection", features);
  }

  public record RouteView(String agencyId, String id, String shortName, String longName, int type,
                          String color, String textColor, long tripCount) {
  }

  public record StopView(String agencyId, String id, String code, String name, double latitude,
                         double longitude, long tripCount) {
  }

  public record ImportStatus(String agencyId, String sourceUrl, Instant importedAt, int routes,
                             int stops, int trips, int stopTimes, int shapePoints) {
  }

  public record RouteGeometryCollection(String type, List<RouteGeometryFeature> features) {
  }

  public record RouteGeometryFeature(String type, RouteGeometryProperties properties,
                                     LineStringGeometry geometry) {
  }

  public record RouteGeometryProperties(String routeId, String shortName, String longName,
                                        String color, String shapeId, Integer directionId) {
  }

  public record LineStringGeometry(String type, List<List<Double>> coordinates) {
  }

  private record ShapeCoordinate(String routeId, String shortName, String longName, String color,
                                 String shapeId, Integer directionId, double longitude,
                                 double latitude) {
  }

  private record RouteGeometryBuilder(ShapeCoordinate metadata, List<List<Double>> coordinates) {
    private RouteGeometryBuilder(ShapeCoordinate metadata) {
      this(metadata, new java.util.ArrayList<>());
    }

    private RouteGeometryFeature feature() {
      return new RouteGeometryFeature("Feature",
          new RouteGeometryProperties(metadata.routeId(), metadata.shortName(), metadata.longName(),
              metadata.color() == null ? "#9bea62" : "#" + metadata.color().replace("#", ""),
              metadata.shapeId(), metadata.directionId()),
          new LineStringGeometry("LineString", List.copyOf(coordinates)));
    }
  }
}
