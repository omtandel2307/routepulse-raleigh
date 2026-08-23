package com.routepulse.schedule;

import com.routepulse.config.RoutePulseProperties;
import com.routepulse.schedule.GtfsScheduleArchive.Calendar;
import com.routepulse.schedule.GtfsScheduleArchive.Route;
import com.routepulse.schedule.GtfsScheduleArchive.ScheduleData;
import com.routepulse.schedule.GtfsScheduleArchive.ServiceException;
import com.routepulse.schedule.GtfsScheduleArchive.Stop;
import com.routepulse.schedule.GtfsScheduleArchive.StopTime;
import com.routepulse.schedule.GtfsScheduleArchive.Trip;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

@Service
public class ScheduleImportService {

  private static final int BATCH_SIZE = 1_000;

  private final RoutePulseProperties properties;
  private final GtfsScheduleClient client;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;

  public ScheduleImportService(RoutePulseProperties properties, GtfsScheduleClient client,
                               JdbcTemplate jdbc, TransactionTemplate transaction) {
    this.properties = properties;
    this.client = client;
    this.jdbc = jdbc;
    this.transaction = transaction;
  }

  public synchronized ImportSummary importAgency(String agencyId) {
    RoutePulseProperties.Agency agency = properties.agencies().stream()
        .filter(candidate -> candidate.id().equals(agencyId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown agency: " + agencyId));

    String sourceUrl = agency.feeds().schedule();
    ScheduleData data = GtfsScheduleArchive.read(client.download(sourceUrl));
    Instant importedAt = Instant.now();

    transaction.executeWithoutResult(status -> {
      clearExisting(agencyId);
      insertRoutes(agencyId, data);
      insertStops(agencyId, data);
      insertCalendars(agencyId, data);
      insertExceptions(agencyId, data);
      insertTrips(agencyId, data);
      insertStopTimes(agencyId, data);
      jdbc.update("""
              INSERT INTO gtfs_import(agency_id, source_url, imported_at, route_count, stop_count,
                                      trip_count, stop_time_count)
              VALUES (?, ?, ?, ?, ?, ?, ?)
              """, agencyId, sourceUrl, Timestamp.from(importedAt), data.routes().size(),
          data.stops().size(), data.trips().size(), data.stopTimes().size());
    });

    return new ImportSummary(agencyId, sourceUrl, importedAt, data.routes().size(), data.stops().size(),
        data.trips().size(), data.stopTimes().size());
  }

  private void clearExisting(String agencyId) {
    jdbc.update("DELETE FROM gtfs_import WHERE agency_id = ?", agencyId);
    jdbc.update("DELETE FROM stop_time WHERE agency_id = ?", agencyId);
    jdbc.update("DELETE FROM transit_trip WHERE agency_id = ?", agencyId);
    jdbc.update("DELETE FROM service_exception WHERE agency_id = ?", agencyId);
    jdbc.update("DELETE FROM service_calendar WHERE agency_id = ?", agencyId);
    jdbc.update("DELETE FROM transit_route WHERE agency_id = ?", agencyId);
    jdbc.update("DELETE FROM transit_stop WHERE agency_id = ?", agencyId);
  }

  private void insertRoutes(String agencyId, ScheduleData data) {
    jdbc.batchUpdate("""
            INSERT INTO transit_route(agency_id, route_id, short_name, long_name, route_type, color, text_color)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, data.routes(), BATCH_SIZE, (statement, route) -> {
      statement.setString(1, agencyId);
      statement.setString(2, route.id());
      statement.setString(3, route.shortName());
      statement.setString(4, route.longName());
      statement.setInt(5, route.type());
      statement.setString(6, route.color());
      statement.setString(7, route.textColor());
    });
  }

  private void insertStops(String agencyId, ScheduleData data) {
    jdbc.batchUpdate("""
            INSERT INTO transit_stop(agency_id, stop_id, stop_code, name, latitude, longitude,
                                     location_type, wheelchair_boarding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, data.stops(), BATCH_SIZE, (statement, stop) -> {
      statement.setString(1, agencyId);
      statement.setString(2, stop.id());
      statement.setString(3, stop.code());
      statement.setString(4, stop.name());
      statement.setDouble(5, stop.latitude());
      statement.setDouble(6, stop.longitude());
      nullableInteger(statement, 7, stop.locationType());
      nullableInteger(statement, 8, stop.wheelchairBoarding());
    });
  }

  private void insertCalendars(String agencyId, ScheduleData data) {
    jdbc.batchUpdate("""
            INSERT INTO service_calendar(agency_id, service_id, monday, tuesday, wednesday, thursday,
                                         friday, saturday, sunday, start_date, end_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, data.calendars(), BATCH_SIZE, (statement, calendar) -> {
      statement.setString(1, agencyId);
      statement.setString(2, calendar.serviceId());
      statement.setBoolean(3, calendar.monday());
      statement.setBoolean(4, calendar.tuesday());
      statement.setBoolean(5, calendar.wednesday());
      statement.setBoolean(6, calendar.thursday());
      statement.setBoolean(7, calendar.friday());
      statement.setBoolean(8, calendar.saturday());
      statement.setBoolean(9, calendar.sunday());
      statement.setObject(10, calendar.startDate());
      statement.setObject(11, calendar.endDate());
    });
  }

  private void insertExceptions(String agencyId, ScheduleData data) {
    jdbc.batchUpdate("""
            INSERT INTO service_exception(agency_id, service_id, service_date, exception_type)
            VALUES (?, ?, ?, ?)
            """, data.exceptions(), BATCH_SIZE, (statement, exception) -> {
      statement.setString(1, agencyId);
      statement.setString(2, exception.serviceId());
      statement.setObject(3, exception.date());
      statement.setInt(4, exception.exceptionType());
    });
  }

  private void insertTrips(String agencyId, ScheduleData data) {
    jdbc.batchUpdate("""
            INSERT INTO transit_trip(agency_id, trip_id, route_id, service_id, headsign, direction_id, shape_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, data.trips(), BATCH_SIZE, (statement, trip) -> {
      statement.setString(1, agencyId);
      statement.setString(2, trip.id());
      statement.setString(3, trip.routeId());
      statement.setString(4, trip.serviceId());
      statement.setString(5, trip.headsign());
      nullableInteger(statement, 6, trip.directionId());
      statement.setString(7, trip.shapeId());
    });
  }

  private void insertStopTimes(String agencyId, ScheduleData data) {
    jdbc.batchUpdate("""
            INSERT INTO stop_time(agency_id, trip_id, stop_sequence, stop_id, arrival_seconds,
                                  departure_seconds, pickup_type, drop_off_type, timepoint, shape_dist_traveled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, data.stopTimes(), BATCH_SIZE, (statement, stopTime) -> {
      statement.setString(1, agencyId);
      statement.setString(2, stopTime.tripId());
      statement.setInt(3, stopTime.sequence());
      statement.setString(4, stopTime.stopId());
      nullableInteger(statement, 5, stopTime.arrivalSeconds());
      nullableInteger(statement, 6, stopTime.departureSeconds());
      nullableInteger(statement, 7, stopTime.pickupType());
      nullableInteger(statement, 8, stopTime.dropOffType());
      nullableInteger(statement, 9, stopTime.timepoint());
      if (stopTime.shapeDistanceTraveled() == null) {
        statement.setNull(10, Types.DOUBLE);
      } else {
        statement.setDouble(10, stopTime.shapeDistanceTraveled());
      }
    });
  }

  private static void nullableInteger(PreparedStatement statement, int index, Integer value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.INTEGER);
    } else {
      statement.setInt(index, value);
    }
  }

  public record ImportSummary(String agencyId, String sourceUrl, Instant importedAt, int routes,
                              int stops, int trips, int stopTimes) {
  }
}
