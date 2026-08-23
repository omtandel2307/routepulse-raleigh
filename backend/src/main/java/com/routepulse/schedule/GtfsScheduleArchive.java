package com.routepulse.schedule;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GtfsScheduleArchive {

  private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
  private static final Set<String> REQUIRED_FILES = Set.of(
      "routes.txt", "stops.txt", "trips.txt", "stop_times.txt"
  );
  private static final Set<String> IMPORTED_FILES = Set.of(
      "routes.txt", "stops.txt", "trips.txt", "stop_times.txt", "calendar.txt", "calendar_dates.txt"
  );
  private static final CSVFormat GTFS_CSV = CSVFormat.RFC4180.builder()
      .setHeader()
      .setSkipHeaderRecord(true)
      .setIgnoreEmptyLines(true)
      .get();

  private GtfsScheduleArchive() {
  }

  public static ScheduleData read(byte[] archive) {
    Map<String, byte[]> files = unzip(archive);
    if (!files.keySet().containsAll(REQUIRED_FILES)) {
      Set<String> missing = new java.util.HashSet<>(REQUIRED_FILES);
      missing.removeAll(files.keySet());
      throw new IllegalArgumentException("GTFS archive is missing required files: " + missing);
    }
    if (!files.containsKey("calendar.txt") && !files.containsKey("calendar_dates.txt")) {
      throw new IllegalArgumentException(
          "GTFS archive must contain calendar.txt, calendar_dates.txt, or both");
    }

    return new ScheduleData(
        parse(files.get("routes.txt"), GtfsScheduleArchive::route),
        parse(files.get("stops.txt"), GtfsScheduleArchive::stop),
        parse(files.get("trips.txt"), GtfsScheduleArchive::trip),
        parse(files.get("stop_times.txt"), GtfsScheduleArchive::stopTime),
        files.containsKey("calendar.txt")
            ? parse(files.get("calendar.txt"), GtfsScheduleArchive::calendar)
            : List.of(),
        files.containsKey("calendar_dates.txt")
            ? parse(files.get("calendar_dates.txt"), GtfsScheduleArchive::exception)
            : List.of()
    );
  }

  static Integer parseGtfsTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String[] parts = value.split(":", -1);
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid GTFS time: " + value);
    }
    int hours = Integer.parseInt(parts[0]);
    int minutes = Integer.parseInt(parts[1]);
    int seconds = Integer.parseInt(parts[2]);
    if (hours < 0 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
      throw new IllegalArgumentException("Invalid GTFS time: " + value);
    }
    return hours * 3600 + minutes * 60 + seconds;
  }

  private static Map<String, byte[]> unzip(byte[] archive) {
    Map<String, byte[]> files = new HashMap<>();
    long totalBytes = 0;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String filename = entry.getName().replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (!entry.isDirectory() && IMPORTED_FILES.contains(filename)) {
          ByteArrayOutputStream output = new ByteArrayOutputStream();
          byte[] buffer = new byte[8192];
          int read;
          while ((read = zip.read(buffer)) != -1) {
            totalBytes += read;
            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
              throw new IllegalArgumentException("GTFS archive exceeds the 100 MB import limit");
            }
            output.write(buffer, 0, read);
          }
          files.put(filename, output.toByteArray());
        }
        zip.closeEntry();
      }
      return files;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to read GTFS archive", exception);
    }
  }

  private static <T> List<T> parse(byte[] contents, RecordMapper<T> mapper) {
    List<T> values = new ArrayList<>();
    try (Reader reader = new InputStreamReader(new ByteArrayInputStream(contents), StandardCharsets.UTF_8)) {
      for (CSVRecord record : GTFS_CSV.parse(reader)) {
        values.add(mapper.map(record));
      }
      return List.copyOf(values);
    } catch (IOException | RuntimeException exception) {
      throw new IllegalArgumentException("Unable to parse GTFS CSV record", exception);
    }
  }

  private static Route route(CSVRecord row) {
    return new Route(required(row, "route_id"), value(row, "route_short_name"),
        value(row, "route_long_name"), integer(row, "route_type"),
        value(row, "route_color"), value(row, "route_text_color"));
  }

  private static Stop stop(CSVRecord row) {
    return new Stop(required(row, "stop_id"), value(row, "stop_code"), required(row, "stop_name"),
        decimal(row, "stop_lat"), decimal(row, "stop_lon"), nullableInteger(row, "location_type"),
        nullableInteger(row, "wheelchair_boarding"));
  }

  private static Trip trip(CSVRecord row) {
    return new Trip(required(row, "trip_id"), required(row, "route_id"), required(row, "service_id"),
        value(row, "trip_headsign"), nullableInteger(row, "direction_id"), value(row, "shape_id"));
  }

  private static StopTime stopTime(CSVRecord row) {
    return new StopTime(required(row, "trip_id"), required(row, "stop_id"), integer(row, "stop_sequence"),
        parseGtfsTime(value(row, "arrival_time")), parseGtfsTime(value(row, "departure_time")),
        nullableInteger(row, "pickup_type"), nullableInteger(row, "drop_off_type"),
        nullableInteger(row, "timepoint"), nullableDecimal(row, "shape_dist_traveled"));
  }

  private static Calendar calendar(CSVRecord row) {
    return new Calendar(required(row, "service_id"), flag(row, "monday"), flag(row, "tuesday"),
        flag(row, "wednesday"), flag(row, "thursday"), flag(row, "friday"), flag(row, "saturday"),
        flag(row, "sunday"), date(row, "start_date"), date(row, "end_date"));
  }

  private static ServiceException exception(CSVRecord row) {
    return new ServiceException(required(row, "service_id"), date(row, "date"),
        integer(row, "exception_type"));
  }

  private static String required(CSVRecord row, String name) {
    String result = value(row, name);
    if (result == null) {
      throw new IllegalArgumentException("Required GTFS field is empty: " + name);
    }
    return result;
  }

  private static String value(CSVRecord row, String name) {
    if (!row.isMapped(name)) {
      return null;
    }
    String value = row.get(name).strip();
    return value.isEmpty() ? null : value;
  }

  private static int integer(CSVRecord row, String name) {
    return Integer.parseInt(required(row, name));
  }

  private static Integer nullableInteger(CSVRecord row, String name) {
    String value = value(row, name);
    return value == null ? null : Integer.valueOf(value);
  }

  private static double decimal(CSVRecord row, String name) {
    return Double.parseDouble(required(row, name));
  }

  private static Double nullableDecimal(CSVRecord row, String name) {
    String value = value(row, name);
    return value == null ? null : Double.valueOf(value);
  }

  private static boolean flag(CSVRecord row, String name) {
    return integer(row, name) == 1;
  }

  private static LocalDate date(CSVRecord row, String name) {
    return LocalDate.parse(required(row, name), DateTimeFormatter.BASIC_ISO_DATE);
  }

  @FunctionalInterface
  private interface RecordMapper<T> {
    T map(CSVRecord row);
  }

  public record ScheduleData(List<Route> routes, List<Stop> stops, List<Trip> trips,
                             List<StopTime> stopTimes, List<Calendar> calendars,
                             List<ServiceException> exceptions) {
  }

  public record Route(String id, String shortName, String longName, int type, String color,
                      String textColor) {
  }

  public record Stop(String id, String code, String name, double latitude, double longitude,
                     Integer locationType, Integer wheelchairBoarding) {
  }

  public record Trip(String id, String routeId, String serviceId, String headsign,
                     Integer directionId, String shapeId) {
  }

  public record StopTime(String tripId, String stopId, int sequence, Integer arrivalSeconds,
                         Integer departureSeconds, Integer pickupType, Integer dropOffType,
                         Integer timepoint, Double shapeDistanceTraveled) {
  }

  public record Calendar(String serviceId, boolean monday, boolean tuesday, boolean wednesday,
                         boolean thursday, boolean friday, boolean saturday, boolean sunday,
                         LocalDate startDate, LocalDate endDate) {
  }

  public record ServiceException(String serviceId, LocalDate date, int exceptionType) {
  }
}
